# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

- New Carpet rule `antiNoFall` (off by default, category SURVIVAL): the server checks for itself whether a player is standing on something before it ends their fall, so no-fall cheats still take fall damage; honest players notice nothing.
- Rule `redstoneScopeLogger` is now off by default, like vanilla (#4): Redstone Scope records only on servers that switch it on (`/carpet setDefault redstoneScopeLogger true`).
- New Carpet rule `litematicaAccuratePlacement` (off by default) (#6): blocks placed by Litematica Upgraded's printer and Easy Place come out in the schematic's exact state (stair halves, open trapdoors and gates, levers switched on), in one click, on UC servers and in CarpetSP worlds.
- New Carpet rule `litematicaShulkerRestock` (off by default) (#3): Litematica Upgraded's printer and Easy Place can take the items they need out of shulker boxes in your inventory, on UC servers and in CarpetSP worlds.
