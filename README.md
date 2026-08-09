<div align="center">

<img src="assets/rlogin.svg" alt="rLogin" width="120" height="120">

# rLogin

**Premium players join without typing anything. Everyone else logs in with a password.**

Authentication for Paper, Folia and Velocity — including on a standalone `online-mode: false` server, with real Mojang UUIDs and skins.

[![Hangar](https://img.shields.io/badge/Hangar-Download-004ee9?logo=papermc&logoColor=white&style=for-the-badge)](https://hangar.papermc.io/raimondarias/rlg)
[![Modrinth](https://img.shields.io/modrinth/dt/rlg?logo=modrinth&logoColor=white&label=Modrinth&color=00AF5C&style=for-the-badge)](https://modrinth.com/plugin/rlg)
[![Docs](https://img.shields.io/badge/Docs-pyrelight-ef7025?logo=readthedocs&logoColor=white&style=for-the-badge)](https://pyrelight.mintlify.app/rlogin/introduction)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/5tuSrNRk3a)
[![Stars](https://img.shields.io/github/stars/pyrelightmc/rlogin?logo=github&logoColor=white&label=Stars&color=eec9bc&style=for-the-badge)](https://github.com/pyrelightmc/rlogin/stargazers)
[![License](https://img.shields.io/github/license/pyrelightmc/rlogin?color=4fa1ab&style=for-the-badge)](LICENSE)

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
| **Server** | Paper, Folia or Velocity (3.x and 4.x) |
| **Minecraft** | 1.21 or newer |
| **Java** | 21 or newer |
| **[PacketEvents](https://modrinth.com/plugin/packetevents)** | Required on a standalone `online-mode: false` server — that is where rLogin does the Mojang verification itself. Not needed behind a proxy or with `online-mode: true`. |

## Installation

There is **one** download, `rLogin-<version>.jar`, and it runs on both
platforms — the same file goes into a Paper/Folia server's `plugins/` and into
a Velocity proxy's.

1. Drop `rLogin-<version>.jar` into `plugins/` on every server. Running a proxy?
   The same jar goes into the proxy's `plugins/` too.
2. Set `online-mode=false` — in `server.properties`, and in `velocity.toml` if
   you run a proxy. rLogin refuses to start otherwise, because with it on the
   server turns away every non-premium player before rLogin is consulted.
3. Standalone? Install
   [PacketEvents](https://modrinth.com/plugin/packetevents) next to it.
4. Start the server. There is nothing else to configure — premium auto-login
   turns itself on wherever it is needed.

Premium-only server? Set `general.auth-mode: online` instead, and keep
`online-mode` on — there rLogin lets the server do the verifying.

Everything else — MySQL, 2FA, the authentication lobby, spawn points,
importing from AuthMe — is optional and documented below.

## Documentation

**[pyrelight.mintlify.app](https://pyrelight.mintlify.app/rlogin/introduction)** —
installation, every setting explained, commands, permissions, and
troubleshooting.

Downloads: [Hangar](https://hangar.papermc.io/raimondarias/rlg) ·
[Modrinth](https://modrinth.com/plugin/rlg) ·
[GitHub Releases](https://github.com/pyrelightmc/rlogin/releases)

## Support

Questions, bug reports and feature requests are welcome in
[Discord](https://discord.gg/5tuSrNRk3a), or as a
[GitHub issue](https://github.com/pyrelightmc/rlogin/issues).

## Building from source

```bash
./gradlew build
```

Requires JDK 21. The distributable lands in `rlogin-plugin/build/libs/` as
`rLogin-<version>.jar`; the per-module jars next to it are intermediate build
output, not releases.

## License

[MIT](LICENSE) © Raimond Arias. PacketEvents is a separate GPL-3.0 plugin that
rLogin talks to at runtime; it is never bundled.
