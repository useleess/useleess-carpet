# Useleess Carpet changelog

## 0.2.0 (beta) - 2026-11-02

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
- The installer window and the server's own messages now come in 13 more languages (Chinese simplified + traditional,
  Russian, Ukrainian, Spanish, French, Italian, Portuguese (Brazil), Japanese, Korean, Swedish, Turkish, German),
  following the language of the PC; English otherwise.

## 0.1.4 - 2026-10-30

- Behind the scenes: the build now always uses the newest Redstone Scope directly. Nothing changes for players;
  install and use it exactly like 0.1.3.

## 0.1.3 - 2026-10-30

- Double-click the installer: a window shows the progress, finds Java 8 on your PC (also the Minecraft launcher's
  own), asks you to accept Mojang's EULA and starts the server in its own window. Before, a double-click only
  flashed a console window.
- Start script: the installer makes `start-useleess-carpet.bat`; double-click that from then on (the window shows
  it in a big yellow box and stays open until you close it). Your own `start.bat` (or `run.sh`, ...) is never
  changed; the installer only tells you it still starts the old jar.

## 0.1.2 - 2026-10-30

- The installer deletes itself once the server jar is built (on Windows right after the server stops): it is not
  needed any more. From then on start the server with `java -jar useleess-carpet-1.12.2-server.jar nogui`; the
  installer prints this command. To update, put the new installer in the server folder and start it once.

## 0.1.1 - 2026-10-30

- Tidier server folder: the launcher now builds ONE file, `useleess-carpet-1.12.2-server.jar`, next to itself and
  deletes all its temporary files, Mojang's jar included. The `useleess-carpet` folder made by 0.1.0 is removed
  automatically. Keep starting the server with the launcher jar as before.
- `--install-only` without a file name now leaves the server jar in the server folder.

## 0.1.0 - 2026-10-30

- First version: one launcher jar that builds a 1.12.2 Carpet server with Carpet, Bullet, Kisde, VasTech and
  Redstone Scope in it, from Mojang's official server jar, on your own PC. Contains no Mojang code.
- `--install-only` writes the finished server jar for hosts and other tools.
- Uses your own copy of Mojang's server jar when there is no internet.
# Changelog

(no releases yet)
