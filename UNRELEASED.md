# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

## New
- Bot sleeping is now part of Carpet's own command: `/player <bot> autosleep [on|off]` (tab completion included). The separate `/autosleep` command is gone (#14).
- New rule `creativeNoClipNoPortals` (default false): players flying in creative mode are not taken by nether portals, end portals or end gateways, handy with `creativeNoClip` (UCC #7).
- New rule `obsidianPlatformNoRegen` (default false): players arriving in the End get no new obsidian platform (#15).

## Fixes
- Litematica Upgraded's Easy Place and printer no longer put cauldrons down already full of water (or snow with extra layers, cake already eaten, saplings already grown) when the schematic saved them that way: the server only sets what a player chooses by placing (LU #16).
