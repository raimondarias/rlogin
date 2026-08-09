<div align="center">

# rLogin

**Premium players join without typing anything. Everyone else logs in with a password.**

Authentication for Paper, Folia and Velocity — including on a standalone `online-mode: false` server, with real Mojang UUIDs and skins.

[![Modrinth](https://img.shields.io/modrinth/dt/rlogin?logo=modrinth&logoColor=white&label=Modrinth&color=00AF5C&style=for-the-badge)](https://modrinth.com/plugin/rlogin)
[![Hangar](https://img.shields.io/badge/Hangar-Download-004ee9?logo=papermc&logoColor=white&style=for-the-badge)](https://hangar.papermc.io/raimondarias/rlg)
[![Docs](https://img.shields.io/badge/Docs-pyrelight-ef7025?logo=readthedocs&logoColor=white&style=for-the-badge)](https://pyrelight.mintlify.app)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/5tuSrNRk3a)
[![Stars](https://img.shields.io/github/stars/raimondarias/rlogin?logo=github&logoColor=white&label=Stars&color=eec9bc&style=for-the-badge)](https://github.com/raimondarias/rlogin/stargazers)
[![License](https://img.shields.io/github/license/raimondarias/rlogin?color=4fa1ab&style=for-the-badge)](LICENSE)

</div>

---

Most authentication plugins make you choose: run `online-mode: true` and lose
every player without an account, or run offline and ask everybody for a
password — the owners of real accounts included.

rLogin does neither. It verifies premium accounts against Mojang itself, so
they are let straight in with **their genuine UUID and skin**, while everyone
else registers with a password as usual. It works the same on a single server
as it does behind a proxy, and 17 languages ship with it.

## Requirements

| | |
|---|---|
| **Server** | Paper, Folia or Velocity |
| **Minecraft** | 1.21 or newer |
| **Java** | 21 or newer |
| **[PacketEvents](https://modrinth.com/plugin/packetevents)** | Required on a standalone `online-mode: false` server — that is where rLogin does the Mojang verification itself. Not needed behind a proxy or with `online-mode: true`. |

## Installation

1. Drop `rlogin-paper.jar` into `plugins/` on every backend server. Running a
   proxy? Also drop `rlogin-velocity.jar` into the proxy's `plugins/`.
2. Standalone `online-mode: false`? Install
   [PacketEvents](https://modrinth.com/plugin/packetevents) next to it.
3. Start the server. There is nothing to configure — premium auto-login turns
   itself on wherever it is needed.

Everything else — MySQL, 2FA, the authentication lobby, spawn points,
importing from AuthMe — is optional and documented below.

## Documentation

**[pyrelight.mintlify.app](https://pyrelight.mintlify.app)** — installation,
every setting explained, commands, permissions, and troubleshooting.

## Support

Questions, bug reports and feature requests are welcome in
[Discord](https://discord.gg/5tuSrNRk3a), or as a
[GitHub issue](https://github.com/raimondarias/rlogin/issues).

## Building from source

```bash
./gradlew build
```

Requires JDK 21. The jars land in `rlogin-paper/build/libs/` and
`rlogin-velocity/build/libs/`.

## License

[MIT](LICENSE) © Raimond Arias. PacketEvents is a separate GPL-3.0 plugin that
rLogin talks to at runtime; it is never bundled.
