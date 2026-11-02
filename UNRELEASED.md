# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

- New rule `endPortalNoTeleportPlayer` (Carpet rule, default off): while on, players touching an end portal stay
  where they are; items, mobs and other entities still go through, so end-portal farms keep working.
  Set it with `/carpet endPortalNoTeleportPlayer true` (`/carpet setDefault ...` keeps it for the world).
- Fixed: after installing, the launcher told players to start the server with `java -jar start-useleess-carpet.bat`;
  it now says to start `start-useleess-carpet.bat` itself (the jar case keeps `java -jar <jar> nogui`).
- New rule `customRecipes` (default on) and command `/customrecipe` (ops, permission level 2): make your own crafting
  recipes inside one chest-style window, no client mod and no creative mode needed (survival works). Your inventory is
  the item picker (saved to a file first, given back exactly when you leave, also after a crash). Pick an item, click
  the 3x3 grid and the result slot, Save. Buttons: Clear, Any order, All recipes (load, overwrite, delete), Search (an
  anvil window), category and page buttons, Exit. Recipes are saved per world in `useleess-carpet/recipes/<name>.json`
  and work for players with a plain Minecraft client; `carpet customRecipes false` stops them matching without
  deleting them. A broken recipe file is skipped and named in the log and to ops.
- Recipe editor: the search box now starts empty (the hint is in the paper's description) instead of "Type to search".
- Recipe editor: a search looks in every category (the category button says so, and tells you when nothing matches).
- Recipe editor: the search anvil no longer shows an "Enchantment Cost" line.
- Recipe editor: the window title after Save / Load / Delete is short ("Saved: golden_apple", at most 30 characters);
  the full message still goes to chat.
- The start script the installer writes now runs Java with `-XX:+UseG1GC` (smoother memory cleanup, fewer lag
  spikes on Java 8) (#1).
- New rule `redstoneScopeLogger` (default on): off = Redstone Scope records nothing, does no extra work and writes no
  log lines; its commands say it is switched off. `/carpet setDefault redstoneScopeLogger false` keeps it for the
  world. The new master jar also makes Redstone Scope's "too many to record" log line quieter (#2).
- Careful break per player (no new rule): `/log carefulBreak always` puts everything you mine into your inventory
  without the Sneak key, `/log carefulBreak sneak` (the default) only while sneaking, `/log carefulBreak` alone still
  switches it on / off. It needs Kisde's rule `/carpet carefulBreak true`. Each player's choice is remembered over
  server restarts (`useleess-carpet/careful-break.json` in the world folder).
