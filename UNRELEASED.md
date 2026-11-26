# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

## Fixed

- #18: a lagging server (below 20 TPS, e.g. a big furnace array) no longer shuts itself down with "Watching Server" /
  "A single server tick took ...". Carpet's own fix for this, the rule `watchdogFix`, is now on by default
  (`/carpet watchdogFix false` turns it off again). A tick that is really stuck still stops the server as before.
