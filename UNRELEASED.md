# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

- New rule `endPortalNoTeleportPlayer` (Carpet rule, default off): while on, players touching an end portal stay
  where they are; items, mobs and other entities still go through, so end-portal farms keep working.
  Set it with `/carpet endPortalNoTeleportPlayer true` (`/carpet setDefault ...` keeps it for the world).
- Fixed: after installing, the launcher told players to start the server with `java -jar start-useleess-carpet.bat`;
  it now says to start `start-useleess-carpet.bat` itself (the jar case keeps `java -jar <jar> nogui`).
