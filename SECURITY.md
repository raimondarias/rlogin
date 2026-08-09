# Security policy

rLogin holds passwords. If you find a way to get in without one, or a way to
read what should not be readable, that matters more than any feature on the
roadmap.

## Reporting

**Do not open a public issue for a vulnerability.**

Report it privately through
[GitHub Security Advisories](https://github.com/pyrelightmc/rlogin/security/advisories/new),
or by direct message on [Discord](https://discord.gg/5tuSrNRk3a) if that is
easier.

Useful to include, as far as you have it:

- What version, and which setup (`Setup:` from the startup line, and your
  `auth-mode`)
- The steps, or a jar/script that shows it
- What it gets an attacker

You will get an acknowledgement within a few days. If a fix needs a release,
you will be credited unless you would rather not be.

## Supported versions

The latest release. rLogin is small and moves quickly enough that backporting
to older versions would give a false impression of coverage.

## What already holds

Reports about these are still welcome — knowing where they fail is the point:

- **Passwords are bcrypt**, never stored or logged in plain text. The log
  filter is not optional.
- **A name lookup is never treated as verification.** Premium status comes
  from Mojang's `hasJoined` after a real encryption handshake — proof the
  client owns the account, not that the name exists.
- **Lockouts apply to addresses, never accounts**, so nobody can lock a player
  out of their own account by failing logins on purpose.
- **Registrations are capped per address**, so accounts cannot be created in
  bulk.
- **Recovery codes are stored as hashes** and work once each.
- **rLogin refuses to start on a setup it cannot make safe** rather than
  running in a state that looks fine and is not.

## What is not a vulnerability

- **"Remember me" trusts an IP address.** This is documented, is 30 minutes by
  default, is excluded for accounts with 2FA, and can be turned off. The
  trade-off is the feature.
- **Cracked players can use any unclaimed name.** That is what an offline-mode
  server is. `premium.protect-premium-names` covers the case that matters.
- **An administrator can read the database.** They own the server.
