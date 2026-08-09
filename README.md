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

### Standalone hybrid mode (`premium.standalone-hybrid-mode`, experimental)

Off by default. Requires installing the separate
[PacketEvents](https://modrinth.com/plugin/packetevents) plugin (not
bundled) alongside rLogin, then setting `premium.standalone-hybrid-mode: true`
in `config.yml`. With it, a single `online-mode: false` backend does this
for every connecting premium account, no Velocity involved:

1. Intercepts the login handshake and asks Mojang whether the connecting
   name is real premium (same lookup as the Velocity path, cached).
2. If it is, sends a real encryption request — the client shows its own
   "Encrypting..." screen and calls Mojang's session server on its own,
   exactly like it would against an `online-mode: true` server.
3. Decrypts the response, turns on encryption on that one connection, and
   double-checks with Mojang's `hasJoined` endpoint that the client
   genuinely holds that account's session (not just that the username
   exists) — an attacker who only knows the username cannot fake this.
4. Takes the real UUID and the signed skin out of that same `hasJoined`
   response and hands them to the connection, so the player joins as their
   genuine Mojang account rather than as an offline stand-in
   (`premium.standalone-premium-uuid` / `premium.standalone-forward-skin`,
   both on by default).
5. The account auto-logs in with zero typing, same as the Velocity path.
   Cracked connections are entirely unaffected either way.

**How step 4 gets the real UUID without version-specific internals:** it
doesn't reach into Mojang's own (obfuscated, per-version) classes at all.
Spigot — and so Paper and every fork of it — adds two fields to the
server's connection class purely so a front proxy can forward an
already-authenticated identity:

```java
public UUID spoofedUUID;
public com.mojang.authlib.properties.Property[] spoofedProfile;
```

The login handler reads them at exactly the point it would otherwise fall
back to an offline profile. Because these are *Spigot's* members and not
Mojang's, they're never obfuscated and their names have been stable for as
long as proxy forwarding has existed — so filling them in produces
byte-for-byte the same result as a Velocity-forwarded login, on the same
mechanism, with none of the version fragility. Lookup is still backed by a
by-type fallback, and if anything can't be resolved the player simply keeps
the offline UUID and still auto-logins (logged once, at startup).

**One consequence worth knowing:** with real UUIDs on, premium *Steve* and
cracked *Steve* are two separate accounts, exactly as they'd be on any
online-mode server — which is the point, but it does mean an existing
offline-mode database keeps its rows under the old UUIDs. Set
`premium.standalone-premium-uuid: false` if you need them to stay identical.

Falls back to normal cracked login automatically, with a log warning, if
PacketEvents isn't installed, Mojang is unreachable, or anything about the
handshake looks wrong — this never blocks a connection.

## Requirements

- Java 21 or newer (compiled with `--release 21`, runs unchanged on later
  versions).
- Paper 1.21+ (or Folia 1.21+) for the backend.
- Velocity 3.x (optional, only needed for network-wide premium auto-login).

## Installation

rLogin ships as a **single universal jar**, `rLogin-1.0.0.jar` — it
self-detects whether it's running on Paper/Folia or on Velocity and only
enables the relevant half of itself. There's nothing to pick at download
time.

1. Download `rLogin-1.0.0.jar`.
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

## Authentication lobby (Velocity, optional)

If your network wants every unauthenticated player centralized on one
server before they reach the real hub, set these in Velocity's
`config.yml`:

```yaml
lobby:
  auth-server: "auth"      # backend from velocity.toml players land on first if not yet authenticated
  default-server: "hub"    # backend they're sent to once authenticated
```

Premium players skip `auth-server` entirely and go straight to
`default-server`. Non-premium players land on `auth-server`, and get
automatically transferred to `default-server` the instant they finish
`/login` or `/register` there. Leave both blank to disable this and keep
velocity.toml's normal `try` order — nothing changes for networks that
don't want a dedicated auth lobby.

By default, a returning non-premium player with a still-valid "remember me"
session (see [Database](#database) below) still touches `auth-server`
briefly before being transferred to `default-server`, since Velocity only
learns the session is valid once the backend confirms it. To skip that hop
entirely and send them straight to `default-server`, give Velocity **read-only**
access to the same database your backends use — set `database.enabled: true`
under `database:` in Velocity's `config.yml`, pointing at the exact same
database (host/credentials matching your Paper/Folia backends'
`config.yml`). This is entirely optional: leave `database.enabled: false`
(the default) and everything above still works, just with that one extra
hop for remembered sessions.

## Spawn points (Paper/Folia)

`/rlogin spawn` manages named spawn points and assigns one to each of four
situations. If a situation has no spawn assigned, the player simply stays
wherever they last disconnected — Bukkit's own default behavior.

```
/rlogin spawn set <name>           save your current location as a named spawn
/rlogin spawn list                 list all named spawns
/rlogin spawn remove <name>        delete a named spawn

/rlogin spawn join <name|none>     spawn used for an already-authenticated join
                                    (premium auto-login, remembered session, bypass)
/rlogin spawn firstjoin <name|none> spawn used the very first time a player ever joins
/rlogin spawn login <name|none>    spawn used right after a successful /login
/rlogin spawn register <name|none> spawn used right after a successful /register
```

Running any of `join`/`firstjoin`/`login`/`register` with no name shows the
current assignment; passing `none` clears it.

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

Produces `rlogin-plugin/build/libs/rLogin-1.0.0.jar` — the single universal
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
rlogin-plugin/    Packages rlogin-paper + rlogin-velocity into the one rLogin-1.0.0.jar that ships
```

`rlogin-plugin` has no code of its own: Paper and Velocity discover their
entry point in non-conflicting ways (Paper reads `plugin.yml` at the jar
root; Velocity reads a compile-time-generated `velocity-plugin.json`), so
both halves — plus `rlogin-common` and its shaded libraries — coexist in one
jar. Each platform only ever touches its own half; dropping the same jar in
a Paper server that has no Velocity in front of it (or vice versa) is safe.

## License

MIT — see [LICENSE](LICENSE). Author: [raimondarias](https://github.com/raimondarias).
