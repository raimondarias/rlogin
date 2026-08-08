# rLogin

**Automatic premium auto-login + password login for cracked accounts.**
For Paper, Velocity (proxy) and Folia. Java 21→26, Minecraft 1.21→26.x.

> 🇪🇸 Español: rLogin trae mensajes en español incluidos de fábrica — pon
> `general.language: es` en `config.yml`. Más idiomas se pueden añadir
> copiando un `messages_<code>.yml` junto a los existentes.

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

> **Known limitation:** combining "automatic premium" + "cracked with
> password" **on a single Paper/Folia with no proxy in front** isn't
> reliably possible across Minecraft versions without low-level Netty
> packet injection (which some plugins do unofficially, and fragilely). If
> you want that combination, the supported and recommended path is
> **Velocity + Paper/Folia backends**. A standalone server can be 100%
> premium (`online-mode: true`) or 100% cracked (`online-mode: false`,
> normal password login) with no issues at all.

## Requirements

- Java 21 or newer (compiled with `--release 21`, runs unchanged on later
  versions).
- Paper 1.21+ (or Folia 1.21+) for the backend.
- Velocity 3.x (optional, only needed for network-wide premium auto-login).

## Installation

1. Download `rLogin-Paper.jar` (and `rLogin-Velocity.jar` if you run a
   proxy).
2. Drop it into `plugins/` on each Paper/Folia server (and Velocity's
   `plugins/` too, if applicable) and start once to generate `config.yml`.
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

## Database

`database.type: sqlite` (default, zero configuration) or `mysql`
(recommended if several Paper/Folia backends need to share the same
accounts — see `config.yml`).

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

Produces `rlogin-paper/build/libs/rLogin-Paper.jar` and
`rlogin-velocity/build/libs/rLogin-Velocity.jar`. Needs network access to
PaperMC's repository (`repo.papermc.io`), where the Paper API and Velocity
API artifacts live.

## Project structure

```
rlogin-api/       Public interfaces (Storage, Importer) — SPI for third-party addons
rlogin-common/    Pure Java logic: config, i18n, database, security, auth, migration
rlogin-velocity/  Proxy plugin: decides online/offline-mode per connection, auth-lobby routing
rlogin-paper/     Backend plugin: accounts, commands, freezing unauthenticated players, spawn points, Folia
```

## License

MIT — see [LICENSE](LICENSE). Author: [raimondarias](https://github.com/raimondarias).
