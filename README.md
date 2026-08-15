<div align="center">

<img src="assets/rlogin.svg" alt="rLogin" width="120" height="120">

# rLogin

**Premium players join without typing anything. Everyone else logs in with a password.**

[![Documentation](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/ghpages_vector.svg)](https://pyrelight.mintlify.app/rlogin/introduction)
[![Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/5tuSrNRk3a)
[![Ko-fi](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/donate/kofi-plural_vector.svg)](https://ko-fi.com/pyrelightmc)

[![Hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/hangar_vector.svg)](https://hangar.papermc.io/Pyrelight/rlg)
[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/plugin/rlg)
[![GitHub](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg)](https://github.com/pyrelightmc/rlogin/releases)

</div>

## What is rLogin?

Most authentication plugins make you choose: run `online-mode: true` and lose every
player without an account, or run offline and ask everybody for a password — the
owners of real accounts included.

rLogin does neither. It verifies premium accounts against Mojang itself, so they are
let straight in with **their genuine UUID and skin**, while everyone else registers
with a password as usual. It works the same on a single server as it does behind a
proxy, and 17 languages ship with it.

## Where can I use rLogin?

<div align="center">

[![Paper](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg)](https://papermc.io)
[![Purpur](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg)](https://purpurmc.org)
[![Folia](https://raw.githubusercontent.com/pyrelightmc/rlogin/main/assets/folia.svg)](https://papermc.io/software/folia)
[![Velocity](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/velocity_vector.svg)](https://papermc.io/software/velocity)

**Not compatible:**

[![BungeeCord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/unsupported/bungeecord_vector.svg)](https://github.com/SpigotMC/BungeeCord)

</div>

One download, `rLogin-<version>.jar`, runs on all of them: the same file goes into a
Paper/Folia server's `plugins/` and into a Velocity proxy's. Velocity **3.x and 4.x**
both work. Velocity is the only proxy rLogin has a plugin for — a backend behind
BungeeCord will detect the forwarding and stop double-verifying, but nothing routes
the login, so players are asked again on every server switch.

## Features

- **Real Mojang UUIDs and skins** on a standalone `online-mode: false` server
- **Password login** for everyone else — frozen in place until they register
- **Two-factor authentication** (TOTP) through any authenticator app
- **"Remember me"** sessions, so a quick reconnect doesn't ask again
- **Brute-force protection** with escalating lockouts by address, never by account
- **Premium name protection** — cracked players can't claim a real account's name
- **Passwords never reach your logs**, and that is not a setting anyone can turn off
- **17 languages**, every message editable
- **Spawn points** for joining, first join, logging in and registering
- **Bedrock support** through Geyser/Floodgate
- **AuthMe import**, hashes included
- **MySQL or SQLite**

## Requirements

| | |
|---|---|
| **Server** | Paper, Folia or Velocity |
| **Minecraft** | 1.21 or newer |
| **Java** | 21 or newer |
| **[PacketEvents](https://modrinth.com/plugin/packetevents)** | Required on a standalone `online-mode: false` server — that is where rLogin does the Mojang verification itself. Not needed behind a proxy, or with `auth-mode: online`. |

## Installation

1. Drop `rLogin-<version>.jar` into `plugins/` on every server, proxy included.
2. Set `online-mode=false` — in `server.properties`, and in `velocity.toml` if you run
   a proxy.
3. Standalone? Install [PacketEvents](https://modrinth.com/plugin/packetevents) next
   to it.
4. Start. There is nothing else to configure.

### What online-mode has to be

rLogin refuses every connection when these disagree, and says so — a silent
half-working server is worse than one that won't start.

| `general.auth-mode` | `server.properties` | `velocity.toml` | PacketEvents |
|---|---|---|---|
| `auto` (default) | `online-mode=false` | `online-mode = false` | Standalone only |
| `offline` | `online-mode=false` | `online-mode = false` | Never |
| `online` | `online-mode=true` | `online-mode = true` | Never |

With online-mode on, the server or proxy turns away every player without a Minecraft
account **before rLogin is consulted at all**. On a server built for those players
that is the whole audience gone.

## Documentation

Every setting, all commands and permissions, and troubleshooting:
**[pyrelight.mintlify.app](https://pyrelight.mintlify.app/rlogin/introduction)**

## What if I need support?

Join the [Discord](https://discord.gg/5tuSrNRk3a) and we will help you out, or open a
[GitHub issue](https://github.com/pyrelightmc/rlogin/issues).

## Want to support us and the plugin?

rLogin is free, and stays free, so that any server can use it. If it saved you some
trouble and you would like to say thanks, a donation helps keep the work going.

[![Ko-fi](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/donate/kofi-plural_vector.svg)](https://ko-fi.com/pyrelightmc)

## Sponsors

Thanks to the following sponsor for supporting this project:

[<img src="assets/xerohost.png" alt="XeroHost" height="46">](https://www.xerohost.net)

## Statistics

[![bStats](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/bstats_vector.svg)](https://bstats.org/plugin/bukkit/rLogin%20Paper/33271)

[![rLogin Paper](https://bstats.org/signatures/bukkit/rLogin%20Paper.svg)](https://bstats.org/plugin/bukkit/rLogin%20Paper/33271)

[![rLogin Velocity](https://bstats.org/signatures/velocity/rLogin%20Velocity.svg)](https://bstats.org/plugin/velocity/rLogin%20Velocity/33272)

## Building from source

[![Java](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/built-with/java_vector.svg)](https://www.java.com)
[![Gradle](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/built-with/gradle_vector.svg)](https://gradle.org)
[![Java 21+](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/built-with/java21_vector.svg)](https://www.java.com)

```bash
./gradlew build
```

Requires JDK 21. The distributable lands in `rlogin-plugin/build/libs/` as
`rLogin-<version>.jar`; the per-module jars next to it are intermediate build output,
not releases.

## License

[MIT](LICENSE) © Raimond Arias. PacketEvents is a separate GPL-3.0 plugin that rLogin
talks to at runtime; it is never bundled.
