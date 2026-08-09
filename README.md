<div align="center">

<img src="assets/rlogin.svg" alt="rLogin" width="120" height="120">

# rLogin

**Premium players join without typing anything. Everyone else logs in with a password.**

[![Documentation](https://img.shields.io/badge/Read%20the-Documentation-ef7025?style=for-the-badge&logo=gitbook&logoColor=white)](https://pyrelight.mintlify.app/rlogin/introduction)
[![Discord](https://img.shields.io/badge/Chat%20with%20us%20on-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/5tuSrNRk3a)
[![GitHub](https://img.shields.io/badge/Source%20on-GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/pyrelightmc/rlogin)

[![Hangar](https://img.shields.io/badge/Download%20on-Hangar-004ee9?style=for-the-badge&logo=papermc&logoColor=white)](https://hangar.papermc.io/raimondarias/rlg)
[![Modrinth](https://img.shields.io/modrinth/dt/rlg?style=for-the-badge&logo=modrinth&logoColor=white&label=Modrinth&color=00AF5C)](https://modrinth.com/plugin/rlg)
[![Stars](https://img.shields.io/github/stars/pyrelightmc/rlogin?style=for-the-badge&logo=github&logoColor=white&label=Stars&color=eec9bc)](https://github.com/pyrelightmc/rlogin/stargazers)
[![License](https://img.shields.io/github/license/pyrelightmc/rlogin?style=for-the-badge&color=4fa1ab)](LICENSE)

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

[![Paper](https://img.shields.io/badge/Available%20for-Paper-2c2c2c?style=for-the-badge&logo=papermc&logoColor=white)](https://papermc.io)
[![Purpur](https://img.shields.io/badge/Available%20for-Purpur-c68fff?style=for-the-badge&logoColor=white)](https://purpurmc.org)
[![Folia](https://img.shields.io/badge/Available%20for-Folia-1f8a70?style=for-the-badge&logo=papermc&logoColor=white)](https://papermc.io/software/folia)
[![Velocity](https://img.shields.io/badge/Available%20for-Velocity-1899d6?style=for-the-badge&logo=papermc&logoColor=white)](https://papermc.io/software/velocity)

</div>

One download, `rLogin-<version>.jar`, runs on all of them — the same file goes into a
Paper/Folia server's `plugins/` and into a Velocity proxy's. Velocity **3.x and 4.x**
both work.

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

## Sponsors

Thanks to the following sponsor for supporting this project:

<div align="center">

[<img src="assets/xerohost.png" alt="XeroHost" height="46">](https://xerohost.net)

</div>

## Statistics

<div align="center">

[![rLogin Paper](https://bstats.org/signatures/bukkit/rLogin%20Paper.svg)](https://bstats.org/plugin/bukkit/rLogin%20Paper/33271)

[![rLogin Velocity](https://bstats.org/signatures/velocity/rLogin%20Velocity.svg)](https://bstats.org/plugin/velocity/rLogin%20Velocity/33272)

</div>

## Building from source

```bash
./gradlew build
```

Requires JDK 21. The distributable lands in `rlogin-plugin/build/libs/` as
`rLogin-<version>.jar`; the per-module jars next to it are intermediate build output,
not releases.

## License

[MIT](LICENSE) © Raimond Arias. PacketEvents is a separate GPL-3.0 plugin that rLogin
talks to at runtime; it is never bundled.
