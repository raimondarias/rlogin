# rLogin

**Automatic premium auto-login + password login for cracked accounts.**
For Paper, Velocity (proxy) and Folia. Java 21→26, Minecraft 1.21→26.x.

> 🌍 rLogin trae 17 idiomas incluidos de fábrica — pon `general.language: es`
> (u otro código, ver [Messages & colors](#messages--colors)) en `config.yml`.
> Cualquier otro idioma se puede añadir copiando un `lang_<code>.yml` propio
> dentro de la carpeta `messages/`, junto a los existentes.

## What makes rLogin different?

The promise is simple: **premium accounts join without typing anything**,
and **non-premium accounts log in with `/login`/`/register`** — like
nLogin Premium, but:

- **Cryptographically verified** premium detection via Velocity's Modern
  Forwarding (not just a spoofable Mojang API check).
- **"Remember me"** session by IP: an already-logged-in non-premium player
  isn't asked for their password again on a quick reconnect.
- Switching servers within the same network (same proxy) **never asks to
  log in again**, premium or not, for the whole duration of the connection.
- 2FA (TOTP), brute-force protection and premium-name protection — **all
  optional and configurable**.
- Migration from AuthMe (nLogin/JPremium importers coming, see
  [Roadmap](#roadmap-phase-2)).
- Bedrock support via Floodgate.
- Built for Folia from day one (regional schedulers, never touching
  Bukkit's classic scheduler anywhere that matters).
- Optional **authentication lobby** routing on Velocity, and configurable
  **spawn points** on Paper/Folia for first join, returning join,
  post-login and post-register.

## How premium auto-login works

When there's a **Velocity** proxy in front, rLogin uses the same technique
as reference plugins like FastLogin: the proxy runs in `online-mode: false`,
but on `PreLoginEvent` it checks whether the connecting name is a real
premium account (local cache + the [Mojang API](https://api.mojang.com))
and, if it is, forces the encrypted Mojang handshake **for that connection
only** (`forceOnlineMode()`). The client authenticates on its own, no
password. If it's not premium, the connection goes through in offline mode
and rLogin asks for `/login` on the backend.

On **standalone Paper/Folia** (no proxy) detection is even simpler: the
player's real UUID is compared against the one the server itself would
generate in offline mode for that same name. If they don't match, somebody
already verified it against Mojang (the server itself running
`online-mode: true`, or a proxy with Modern Forwarding) — no extra network
call needed.

Combining "automatic premium" + "cracked with password" **on a single
backend with no proxy in front** normally isn't possible: `online-mode` is
one global toggle, vanilla has no concept of deciding it per-connection.
**Velocity + Paper/Folia backends is still the recommended, most mature
path** for that combination — see above, it's what most of this README
assumes. For a genuinely proxy-less setup, there's also:

### Premium auto-login without a proxy

Combining "automatic premium" and "cracked with a password" on a single
backend is not something vanilla can express: `online-mode` is one global
toggle, and there is no per-connection version of it. rLogin does the
verification itself instead, so both work on one `online-mode: false` server
with nothing in front of it.

**There is nothing to enable.** rLogin reads how your server is set up and
decides: `online-mode: true` or a proxy in front means somebody else already
verified the connection and it stays out of the way; a standalone
`online-mode: false` server means it has to do the verifying, and it does.

That last case **requires the free
[PacketEvents](https://modrinth.com/plugin/packetevents) plugin**, which is
not bundled — PacketEvents is GPL-3.0 and rLogin is MIT. Without it rLogin
refuses every connection and says why, in the console and on the player's
disconnect screen. That is deliberate: switching itself off instead would
leave an offline-mode server with *no* authentication, where anyone could
join under any name, including yours.

What happens on a premium connection:

1. rLogin holds the login and asks Mojang whether the name is really premium.
2. If it is, it sends a real encryption request — the client shows its own
   "Encrypting…" screen and authenticates against Mojang on its own, exactly
   as it would against an `online-mode: true` server.
3. It confirms with Mojang's `hasJoined` that this client genuinely **owns**
   the account, not merely that the username exists. Someone who only knows
   your name cannot fake this.
4. The real UUID and signed skin from that same response are handed to the
   connection, so the player joins as their genuine Mojang account.

**How step 4 works without version-specific internals:** it never touches
Mojang's own (obfuscated, per-version) classes. Spigot — and so Paper and
every fork of it — adds two fields to the server's connection class purely
so a front proxy can forward an already-authenticated identity:

```java
public UUID spoofedUUID;
public com.mojang.authlib.properties.Property[] spoofedProfile;
```

The login handler reads them at exactly the point it would otherwise build an
offline profile. Because these are *Spigot's* members and not Mojang's, they
are never obfuscated and their names have been stable for as long as proxy
forwarding has existed — so filling them in produces byte-for-byte the same
result as a Velocity-forwarded login, on the same mechanism, with none of the
version fragility. Lookup falls back to a search by type, and if anything
cannot be resolved the player simply keeps the offline UUID and still logs in.

### `premium.uuid-type`

| | Premium player | Cracked player |
|---|---|---|
| `real` *(default)* | real Mojang UUID | offline UUID |
| `cracked` | offline UUID | offline UUID |
| `random` | random, kept per name | random, kept per name |

`real` is the only mode where "premium Steve" and "cracked Steve" are two
separate accounts. `cracked` (also accepted as `offline`) keeps an existing
offline-mode world and database working untouched. `random` lets a player
move between premium and cracked without losing their data.

Changing this on a server that already has players changes who they are to
every other plugin. `/rlogin changeuuid` carries an account across if needed.

## Requirements

- Java 21 or newer (compiled with `--release 21`, runs unchanged on later
  versions).
- Paper 1.21+ (or Folia 1.21+) for the backend.
- Velocity 3.x (optional, only needed for network-wide premium auto-login).

## Installation

rLogin ships as a **single universal jar**, `rLogin-1.1.0.jar` — it
self-detects whether it's running on Paper/Folia or on Velocity and only
enables the relevant half of itself. There's nothing to pick at download
time.

1. Download `rLogin-1.1.0.jar`.
2. Drop the *same* jar file into `plugins/` on each Paper/Folia server, and
   into Velocity's `plugins/` too if you run a proxy. Start each once to
   generate `config.yml`.
3. If you're using Velocity:
   - `velocity.toml`: `player-info-forwarding-mode = "modern"`.
   - On each Paper/Folia backend: `online-mode: false` in
     `server.properties`, and in `config/paper-global.yml` →
     `proxies.velocity.enabled: true` + `online-mode: true` (so it trusts
     the forwarding). Copy Velocity's `forwarding.secret` to every backend.
4. Tune `plugins/rLogin/config.yml` (Paper/Folia) and/or
   `plugins/rlogin/config.yml` (Velocity) to your liking, then `/rlogin reload`.

## Proxy setup (Velocity)

rLogin is configured on the **backends**. The proxy's own config is short on
purpose, because the proxy only does two things: verify premium accounts
(always, with nothing to configure — behind a proxy it is the only place that
can), and make sure a player logs in once, on a server that can ask, and is
never asked again while switching servers.

```yaml
login-servers:
  # Servers running rLogin, i.e. the ones that can ask for a password.
  servers:
    - 'auth'
  # Send players there even if Velocity's own "try" order chose elsewhere.
  enforce: true

after-login:
  # stay | send | previous
  action: stay
  servers:
    - 'lobby'
  never-return-to: []

timing:
  switch-delay: 500
  retry-delay: 5000
```

`after-login.action` is a single choice rather than a set of switches:

- **`stay`** — leave them where they are. Right when players log in on the
  lobby they were headed to anyway.
- **`send`** — move them to one of `servers`, picked at random. Right for a
  dedicated auth server they should not linger on.
- **`previous`** — back to the server they were on last time, falling back to
  `send`. Login servers are excluded automatically.

**The database does not go on the proxy.** Accounts live on the backends. If
you run more than one backend, point them all at the same MySQL/MariaDB so an
account made on one is known on the others. The proxy never reads or writes it.

If a server listed under `login-servers` never reports a login, rLogin says so
in the console — that is what a missing plugin or a typo'd name looks like,
and it otherwise shows up as players walking in unauthenticated.

## Spawn points (Paper/Folia)

Four moments, one spawn each. There are no spawn names to invent or keep in
sync: the moment *is* the spawn, and `set` always uses where you are standing.
A moment with no spawn set leaves the player wherever they last disconnected,
which is Bukkit's own default.

```
/rlogin spawn set <moment>        save your current location for that moment
/rlogin spawn remove <moment>     delete it
/rlogin spawn teleport <moment>   go there, to check it is where you meant
/rlogin spawn list                all four, with world and coordinates
```

The moments:

| Moment | When it applies |
|---|---|
| `join` | An already-authenticated join: premium auto-login, remembered session, or `rlogin.bypass` |
| `firstjoin` | The player's very first join ever |
| `login` | Right after a successful `/login` |
| `register` | Right after a successful `/register` |

## Commands

| Command | Alias | Description |
|---|---|---|
| `/login <password> [2fa-code]` | `/l`, `/rlogin login` | Log in |
| `/register <password> <repeat>` | `/reg`, `/rlogin register` | Create your account |
| `/changepassword <current> <new>` | `/rlogin changepassword` | Change your password |
| `/logout` | `/rlogin logout` | Log out |
| `/2fa enable\|disable\|confirm <code>` | `/rlogin 2fa` | Manage 2FA |
| `/premium` | `/rlogin premium` | Check your premium status |

Administration (`rlogin.admin`):

| Command | Description |
|---|---|
| `/rlogin reload` | Reload `config.yml` and messages |
| `/rlogin unregister <player>` | Delete an account |
| `/rlogin forcelogin <player>` | Force-authenticate a player |
| `/rlogin migrate <authme\|nlogin\|jpremium> <path-or-jdbc>` | Import accounts |
| `/rlogin info <player>` | Show account info |
| `/rlogin changeuuid <from> <to>` | Move an account's credentials to another UUID |
| `/rlogin spawn ...` | Manage spawn points (see above) |

## Permissions

- `rlogin.admin` (`op` by default) — admin commands.
- `rlogin.bypass` (`false` by default) — skips the login requirement (NPCs, test bots...).

## Messages & colors

Every message lives in `plugins/rLogin/messages/lang_<code>.yml` (Paper/Folia)
and its equivalent on Velocity, one file per language. `general.language` in
`config.yml` picks which one is used; all bundled files below are always
copied to disk on first run so you can freely edit whichever you use.

**17 languages bundled out of the box:**

| Code | Language | Code | Language | Code | Language |
|------|----------|------|----------|------|----------|
| `en` | English | `pl` | Polski | `ko` | 한국어 |
| `es` | Español | `it` | Italiano | `ar` | العربية |
| `pt_BR` | Português (Brasil) | `nl` | Nederlands | `vi` | Tiếng Việt |
| `de` | Deutsch | `tr` | Türkçe | `id` | Bahasa Indonesia |
| `fr` | Français | `uk` | Українська | | |
| `ru` | Русский | `ja` | 日本語 | | |
| `zh_CN` | 简体中文 | | | | |

Fully custom languages work too, no code changes needed: drop your own
`lang_<code>.yml` (any code — it doesn't have to be one of the 17 above) in
that same `messages/` folder, set `general.language` to that code, and
`/rlogin reload` — rLogin only ever auto-creates the bundled files listed
above; any other file it finds is loaded as-is and never touched or
overwritten.

Any message can freely mix all three color formats rLogin understands, in
the same line if you want:

1. **MiniMessage** (Kyori) — `<red>`, `<#e39fff>`, `<bold>`...
2. **Legacy codes** (classic) — `&a` / `§a`, plus styles (`&l`, `&n`...) and `&r` to reset.
3. **Hex** (BungeeCord/Spigot config style, the default used in the bundled
   files) — `&#RRGGBB`, `§x§R§R§G§G§B§B`, or a bare `#RRGGBB`.

Whatever format you write, it always renders with full hex precision on the
client — there's no lossy downgrade to the 16 legacy colors even for `&a`-style
input. The bundled messages follow this palette:

| Use                     | Color                                          |
|--------------------------|------------------------------------------------|
| Commands                 | `#e39fff` |
| Values / placeholders    | `#fd8ddb` |
| Errors                   | `#fd5e5e` |
| Success                  | `#91f251` |
| Warnings                 | `#fdba5e` |
| Time-related text        | `#5e9dfd` |
| Neutral / general text   | `#d4d9d8` |

## Database

`database.type: sqlite` (default, zero configuration) or `mysql`
(recommended if several Paper/Folia backends need to share the same
accounts — see `config.yml`). This is where account records, passwords and
2FA secrets live — only Paper/Folia ever writes to it.

Velocity never needs this to function — premium detection is per-connection
and cross-backend trust travels over its own `rlogin:sync` channel. Its
`database` section (off by default) is a purely optional, **read-only**
convenience so the auth-lobby can skip an extra hop for remembered
sessions — see [Authentication lobby](#authentication-lobby-velocity-optional).

## Migrating from other plugins

```
/rlogin migrate authme plugins/AuthMe/authme.db
/rlogin migrate authme "jdbc:mysql://user:password@host:3306/authme"
```

Recognizes bcrypt passwords and AuthMe's default SHA256 format (these get
auto-rehashed to bcrypt on the first successful login after migrating).
Other AuthMe algorithms (MD5, WHIRLPOOL...) are still imported but need the
player to register again.

## Roadmap (Phase 2)

This is a living project. Planned for future versions:

- Real importers for nLogin and JPremium/LoginSecurity (right now they
  throw an explicit error instead of pretending to work — PRs welcome).
- QR code generation for 2FA setup (currently gives the secret + an
  `otpauth://` URI as text).
- On-screen captcha after repeated failed attempts.
- bStats metrics.
- A reliable, cross-version standalone hybrid mode (premium + cracked with
  no proxy), if one is ever found.

## Building from source

```
./gradlew build
```

Produces `rlogin-plugin/build/libs/rLogin-1.1.0.jar` — the single universal
jar for both Paper/Folia and Velocity. Needs network access to PaperMC's
repository (`repo.papermc.io`), where the Paper API and Velocity API
artifacts live. Requires JDK 21 (set `JAVA_HOME` to a JDK 21 install if your
default `java` is newer — Gradle 8.14's toolchain resolution can be picky
about very recent JDKs).

## Project structure

```
rlogin-api/       Public interfaces (Storage, Importer) — SPI for third-party addons
rlogin-common/    Pure Java logic: config, i18n, database, security, auth, migration
rlogin-velocity/  Proxy code: decides online/offline-mode per connection, auth-lobby routing
rlogin-paper/     Backend code: accounts, commands, freezing unauthenticated players, spawn points, Folia
rlogin-plugin/    Packages rlogin-paper + rlogin-velocity into the one rLogin-1.1.0.jar that ships
```

`rlogin-plugin` has no code of its own: Paper and Velocity discover their
entry point in non-conflicting ways (Paper reads `plugin.yml` at the jar
root; Velocity reads a compile-time-generated `velocity-plugin.json`), so
both halves — plus `rlogin-common` and its shaded libraries — coexist in one
jar. Each platform only ever touches its own half; dropping the same jar in
a Paper server that has no Velocity in front of it (or vice versa) is safe.

## License

MIT — see [LICENSE](LICENSE). Author: [raimondarias](https://github.com/raimondarias).
