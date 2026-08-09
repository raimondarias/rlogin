# Changelog

Notable changes to rLogin. Dates are release dates.

## 1.1.3 — 2026-08-09

### Fixed

- **The update check had been failing silently since the repository moved to
  the organisation.** GitHub answers `301` for a repository that changed
  owner, and Java's HTTP client follows no redirects by default — so every
  server on an older build was told nothing while two releases went by.
  Redirects are followed now.
- **A successful check no longer looks identical to a broken one.** It only
  ever spoke when it found an update, so silence meant either "up to date" or
  "the check failed" with no way to tell. All four outcomes are reported, on
  the line after `rLogin ready`:

  ```
  [rLogin] You are running the latest release: 1.1.3
  ```

### Added

- **`/rlogin version`** — what is running, and whether it is current. Open to
  every player, not just staff: needing an operator to read a version number
  is why so many bug reports guess at it. The check runs fresh rather than
  repeating what startup found.
- **LuckPerms integration**, optional and absent without a trace when it is
  not installed:
  - **`rlogin:authenticated` context.** A frozen player still holds every
    permission their rank grants — rLogin blocks its own commands, but
    anything asking LuckPerms directly sees a fully-privileged player who has
    not proved who they are. Now you can scope permissions to people who
    actually logged in.
  - **`/rlogin changeuuid` carries the rank across.** LuckPerms keys its data
    by UUID, so under `uuid-type: real` a premium player arrived with a new
    identity and no rank. Copied rather than moved, and refused outright when
    the target already has permissions.
- **The startup line names the server fork**, and bStats reports it. Purpur,
  Pufferfish and the rest need no code — they inherit the Paper API rLogin
  uses — but bStats files them all under "Paper", so there was no way to know
  which fork a report came from.

## 1.1.2 — 2026-08-09

### Security

- **`/rlogin unregister` now invalidates the "remember me" session.** It only
  deleted the account row; the session row survived, and the session check
  never asked whether the account still existed. An unregistered player
  reconnecting from the same address was let straight back in — authenticated,
  with no account and no prompt to make one.
- **Registrations are capped per address** (`security.registration`, 3 per
  hour by default). Brute-force protection guarded guessing an existing
  password; nothing guarded creating accounts, so one address could claim
  every name on the server and grow the database without bound.
- **Weak passwords are refused** (`security.password.reject-common`). Length
  alone accepted `123456`, and a player's own name — which is public.

### Added

- **Account recovery.** `/register` now issues one-time codes, shown once.
  `/recover <code> <new password>` sets a new password and clears 2FA, so
  forgetting a password or losing an authenticator no longer ends at an
  administrator deleting the account — which handed the name to whoever
  registered it next.
- **Events for other plugins.** `RLoginAuthenticateEvent` fires when a player
  is actually free to play, which is the moment to give them things;
  `PlayerJoinEvent` fires while they are still frozen. `RLoginRegisterEvent`
  marks a genuinely new account. Both on the player's own thread, Folia
  included.
- **rlogin-api is published** through JitPack, so addons can compile against
  rLogin instead of copying class names.
- **nLogin and JPremium/LoginSecurity importers.** Both were stubs that threw,
  while `/rlogin migrate` offered them. Table and column names are discovered
  rather than assumed, and an unrecognised layout says so.
- Tests for the Paper half, which had none.
- Releases are built and published from the tag by CI.

### Fixed

- `security.password.bcrypt-cost` had drifted under the wrong parent in the
  bundled config, so it was read as absent and quietly fell back to its
  default.

## 1.1.1 — 2026-08-09

### Added

- **`general.auth-mode`** — `auto`, `online` or `offline`. Says who the server
  is for instead of leaving it to be inferred.
- **`limbo.login-timeout-seconds`** (60) — disconnects a player who never logs
  in. A connection parked at the prompt still holds a player slot.
- Metrics report for real: bStats had been wired in with the plugin id left at
  zero. Paper and Velocity now count separately.

### Fixed

- **rLogin refuses to start when `online-mode` contradicts `auth-mode`.** With
  online-mode on, the server or proxy turns away every player without a
  Minecraft account before any plugin is consulted — on a server built for
  those players, the entire audience, while everything still looked healthy.
- **The premium greeting appears once per connection, not once per server.** A
  player hopping between four backends was told four times.
- The documentation told you to set `online-mode = true` in `velocity.toml`.
  That is the one value that stops rLogin working.

## 1.1.0 — 2026-08-09

- Premium verification without a configuration switch: it turns itself on
  wherever nothing else verifies the connection.
- Config migration on upgrade, keeping your values and comments.
- Brute-force lockouts by address, never by account.

## 1.0.0 — 2026-08-09

First public release. Premium auto-login with real Mojang UUIDs on a
standalone `online-mode: false` server, password login for everyone else,
17 languages, Paper/Folia/Velocity.
