# Useleess Carpet: code map

Read before changing code; update in the same change when files move or are added.

## What it is

A launcher jar (plain Java 8, no libraries) holding a **patch**. On the player's PC it downloads Mojang's official
server jar, applies the patch and gets the master Carpet server jar (Kisde Carpet, which carries Bullet + VasTech,
with Redstone Scope built in), then runs it in the same Java process. No Mojang byte is shipped.

## Shared core (version-free): `src/main/java/com/useleess/carpet/core`

- `Delta`: binary diff of one file: copy from the same-named Mojang file, copy from the **dictionary** (all of
  Mojang's jar back to back, so pieces shared with any Mojang file are copied, not shipped), or insert new bytes.
- `Patch`: the patch file (`useleess-carpet/server.patch` in the jar): per file of the finished jar DIR / SAME /
  FROM (another Mojang file) / DELTA (Mojang file + delta) / NEW (delta against nothing) + its SHA-1. `apply`
  builds the jar and checks every file.
- `Downloader`: Mojang's official jar: the owner's own copy (by SHA-1) or a download from Mojang, checked.
- `VersionInfo`: reads the per-version data file (below).
- `Messages` + `Problem`: every player-visible line comes from `assets/useleess_carpet/lang/en_us.json`.
- `Hashes`: SHA-1.

## Launcher: `src/main/java/com/useleess/carpet/launcher`

- `SelfRemover`: the launcher deletes its own jar when it lies in the server folder (user 2026-10-30: wasted space;
  from then on the server jar is started directly). Windows locks a running jar: a hidden `cmd` helper started from
  a shutdown hook deletes it right after the process ends.
- `Main`: entry point. Command line = `Installer.ensure`, `scripts` (write our own start-useleess-carpet.bat/.sh;
  the owner's scripts are never changed, only named when they start another jar), `SelfRemover`, then the server's
  main class in a child class loader. Double-click (no arguments, no console; `-Duseleess.carpet.gui=true` forces it) = `gui`: the installer
  window, see below.
- Double-click setup (user 2026-10-30): `Window` (Swing: progress lines, the EULA yes/no question, a big yellow
  highlight "From now on, double-click start-useleess-carpet.bat"; stays open until Close; tests answer through
  `-Duseleess.carpet.answers=eula=yes|no` and it closes by itself), `JavaFinder` (a Java 8: this one, JAVA_HOME,
  install folders, the Minecraft launcher's jre-legacy, PATH; each asked for its version), `StartScripts` (our own
  script; finds the owner's scripts only to name them, NEVER changes them - user rule), `Eula` (eula.txt). The server
  then runs as its own program (javaw, Minecraft's server window).
  `--install-only [file]` = only build the server jar (moved to `file` when given).
- `Installer`: ONE file in the server folder, `useleess-carpet-<mc>-server.jar` (user 2026-10-30: no cache folder,
  no Mojang jar left behind). Mojang's jar goes to the temp folder `.useleess-carpet-temp/`, deleted afterwards (also
  on failure). The built jar holds the patch fingerprint in `META-INF/useleess-carpet.stamp`: same = reused,
  missing/damaged/other = rebuilt. 0.1.0's `useleess-carpet/` cache folder is removed (only the files it made).

## Build-time tools (never shipped): `src/tools/java/com/useleess/carpet/tools`

Flow: master jar -> `makeRules` -> rules jar (`build/rules/rules-<mc>.jar`) -> `makePatch` -> patch in the launcher.

- `OurRules`: the table of our own Carpet rules (name, default, Carpet's English description, categories).
- `RulesMaker`: Gradle task `makeRules` (ASM, build-time only): master jar + every `OurRules` line = the rules jar.
  Each rule becomes a `@Rule` `public static boolean` of `carpet/CarpetSettings` (default set at the start of its
  `<clinit>`), so `/carpet`, `carpet.conf` and CarpetSP treat it like Carpet's own. Its hook comes from the
  per-version data `src/tools/resources/useleess-carpet/rules/<mc>.properties` (obfuscated names, checked against
  MCP's `obf_to_srg.tsrg`): `<rule>.method` = the method, `<rule>.skipFor` = "return at its start while the rule is on
  and this local is an instance of that class". A missing data line, class, method, category or Carpet annotation
  fails the build with its name.
- `PatchMaker`: Gradle task `makePatch`: Mojang jar (downloaded into `build/mojang/`) + rules jar -> patch, then
  **LeakCheck** + a full rebuild compared file for file with the rules jar. Any failure fails the build.
- `LeakCheck`: no inserted run of 64+ bytes may appear anywhere in Mojang's jar.

The master jar is Redstone Scope's `buildCarpetJar` output
(`redstone-scope/mod/versions/1.12.2-carpet/Kisde-Carpet-server-1.12.2-RedstoneScope.jar`), used straight from there
on this PC. Test-box and release runs get it fresh through `../box-inputs.json` (discord-bot/testbox.py `inputs`,
copied to `inputs/master-1.12.2.jar` in the run's copy); a jar older than Redstone Scope's code stops the
run/release. No hand-made copies (user 2026-10-30). Override with `-Pmaster=`.

## Server code in the jar: `src/server/java/com/useleess/carpet/server` (op-made custom recipes)

The one part of the mod that runs inside the server. Written in Minecraft's **readable (MCP) names**, compiled
(`compileServerJava`) against ForgeGradle's mapped 1.12.2 jar (its cache, same gradle home as Redstone Scope's build;
compile-only) + the master jar (libraries, Gson), then by `makeRules` turned into SRG names (`tools/McpNames`, MCP's
`srg_to_snapshot` table) and into the obfuscated names (Redstone Scope's `Mappings`, **compiled in from
`../../redstone-scope/.../carpetBuilder/.../Mappings.java`, or `../inputs/rs-remap/Mappings.java` in a box / release copy
(`box-inputs.json`); never copied, never edited**; `src/tools/java/com/redstonescope/carpetbuild/NameBridge` is the public
door to it). `tools/ServerLayer` adds the classes (+ the shared `core` package for `Messages` and the lang file), patches
the six hooks (written in SRG names, `Hooks` is their only target: `CarpetServer.onServerLoaded` (before the worlds load:
commands + the recipe only) / `tick` (first tick: the world folder; then `Hooks.nextTick` actions - Minecraft's
`addScheduledTask` runs at once on the server thread) / `playerConnected`, `PlayerList.playerLoggedOut` (before the
player data is written), `MinecraftServer.stopServer`, `EntityPlayerMP.onDeath` (before the drops)) and `tools/LinkCheck` proves every class / method / field used exists and every abstract method our classes inherit has a
body (Forge's compile jar gives some `IRecipe` methods default bodies the server does not have), else the build fails.
- Game-free (unit tests): `Stuff`, `Product`, `RecipeData` (grid trimming, shaped / any-order matching), `RecipeFile` (our
  JSON format, a bad file = `BadRecipe` with a language key), `RecipeNames`, `ServerText` (English from the lang file).
- `Hooks` -> `Guard` (every entry runs guarded: a failure logs, ends open editors and switches the feature off; notices go
  to ops), `Rule` (reads `carpet.CarpetSettings.customRecipes`, a plain rule without hook), `RecipeStore` (files, live list),
  `CustomRecipes` (the ONE `IRecipe` registered in `CraftingManager.REGISTRY`; asks the store at match time), `ItemCodec`,
  `Catalogue` + `Category` (picker items), `CustomRecipeCommand` (`/customrecipe`, level 2).
- The editor: `Editor` (open sessions), `EditorSession` (all state + every click; shows itself through two fake
  inventories, so the real inventory is never part of the window), `EditorContainer` (chest window, 27 menu + 36 picker
  slots, own click handling only), `SearchContainer` + `SearchWindow` (the anvil search), `Layout` (slot roles),
  `Vault` (saves + empties the real inventory first, restores on exit / close / leave / death / stop / off-switch / next join).
- `SelfTest`: `/uct`, registered only with `-Duseleess.carpet.selftest=1`; the in-game tests drive editors with it by slot role.
- Adding a hook: one `hook(...)` line in `ServerLayer` + a public static method in `Hooks`; `RulesTest` lists the changed classes.

## Per-version layer

Only data: `src/main/resources/useleess-carpet/minecraft/<version>.properties` (Mojang jar SHA-1, Mojang URLs, local
file names, main class), `src/tools/resources/useleess-carpet/rules/<version>.properties` (our rules' hooks) +
`def minecraft` and the master jar path in `build.gradle`.

### Adding a rule

1. One line in `OurRules.ALL` (name, default, description, categories from `CarpetSettings$RuleCategory`).
2. Its hook lines in every `rules/<version>.properties` (a new kind of hook = a new method in `RulesMaker`).
3. Tests: `RulesTest` covers every table line; add in-game tests under `rules/` in `../ingame-test.sh` and raise
   the `rules/` min in `../test-plan.json`; a line in `../README.md` (our rules) and `../UNRELEASED.md`.

### Adding a Minecraft version

1. Add `<version>.properties` (SHA-1 + URL from Mojang's version manifest, piston-meta.mojang.com) and
   `src/tools/resources/useleess-carpet/rules/<version>.properties` (our rules' hooks in that version's names).
2. Build with `-Pmaster=<that version's master jar>` and the new `minecraft` value (one jar per version).
3. Add the version to `ingame-test.sh` checks if its files differ.

## Tests

- Unit (`src/test`, run by `gradlew build`, no game): `PatchTest` (delta, format, leak check), `InstallerTest`
  (download / own copy / cache / mirrors / no internet with a stand-in web), `RulesTest` (every rule a Carpet field
with its default, the hook at the method's start, a missing target fails the build, exactly which classes the rules jar changes / adds; reads `build/rules/`), `RecipeTest` (recipe grid / file format / names), `tools/LinkCheckTest`, `HygieneTest` (language keys, no
  English in code, version data, no copied code blocks).
- In-game (`../ingame-test.sh`, test box): the real launcher jar on real servers: download from Mojang, contents,
  own copy, cache reuse, boot, one rule each of VasTech / Kisde / Bullet, Redstone Scope loaded, clean stop.
  `recipes/`: the custom-recipe editor on its own server with `/uct` (survival op fake players, clicks by slot role).
  `rules/`: our rules on their own server (listed with its default, a Carpet fake player in an end portal stays /
  goes, items still go, `carpet setDefault` kept over a restart). `FILTER=` skips the parts it does not need.
