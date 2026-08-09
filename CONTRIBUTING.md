# Contributing

Pull requests are welcome, including small ones.

## Building

```bash
./gradlew build
```

Requires **JDK 21**. The distributable is `rlogin-plugin/build/libs/rLogin-<version>.jar`
— one jar that runs on both Paper/Folia and Velocity. The per-module jars next
to it are intermediate build output; don't ship those.

## The shape of the project

| Module | What lives there |
|---|---|
| `rlogin-api` | Interfaces and data types with no dependencies. **Published**, so treat changes here as breaking someone else's build. |
| `rlogin-common` | Accounts, passwords, sessions, config, messages, importers. Platform-neutral. |
| `rlogin-paper` | Everything that touches Bukkit, plus the public events. |
| `rlogin-velocity` | Proxy routing and per-player online-mode. Never touches the database. |
| `rlogin-plugin` | Shades the above into the single distributable jar. |

Anything that can live in `rlogin-common` should: it is the half that can be
tested without a server.

## Translations

The most useful contribution, and it needs no Java.

Copy `rlogin-common/src/main/resources/messages/lang_en.yml`, translate the
values, and save it as `lang_<code>.yml` in the same folder. **Keep the key
list identical to `lang_en.yml`** — a missing key shows the raw key to a
player. A test enforces this, so a mismatch fails the build rather than
reaching someone's server.

Translate the meaning, not the words. These are read by players mid-frustration.

## Tests

```bash
./gradlew test
```

New logic wants a test if it can have one. Pure logic goes in `rlogin-common`
or in a package-private method that can be called without a server — see
`ServerTopology.decide`, which exists in that shape for exactly this reason.

There is no mocking framework on purpose; the existing tests use small
hand-written fakes, which stay readable.

## Things that are deliberate

Before "fixing" these, they are choices, not oversights:

- **Password masking in logs cannot be turned off.** An admin who could
  disable it could collect their players' passwords, and people reuse them.
- **Lockouts apply to addresses, never accounts.** Locking accounts lets
  anyone who knows a name keep its owner out.
- **rLogin refuses to start on a contradictory setup** rather than running
  half-broken and silent. See `OnlineModeConflictListener` and
  `MissingPacketEventsListener`.
- **PacketEvents is never bundled.** It is GPL-3.0; rLogin is MIT.

## Commits

Explain *why*, not what — the diff already says what. If a change is not
obvious in six months, the message is where that goes.

## Reporting a bug

Include the startup line rLogin prints, your `Setup` value, and console output
with `general.debug: true`. Most reports are answered by those three.
