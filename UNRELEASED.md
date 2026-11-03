# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

- New Carpet rule `antiNoFall` (off by default, category SURVIVAL): the server checks for itself whether a player is standing on something before it ends their fall, so no-fall cheats still take fall damage; honest players notice nothing.
- Rule `redstoneScopeLogger` is now off by default, like vanilla (#4): Redstone Scope records only on servers that switch it on (`/carpet setDefault redstoneScopeLogger true`).
