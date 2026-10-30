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

- `PatchMaker`: Gradle task `makePatch`: Mojang jar (downloaded into `build/mojang/`) + master jar -> patch, then
  **LeakCheck** + a full rebuild compared file for file with the master jar. Any failure fails the build.
- `LeakCheck`: no inserted run of 64+ bytes may appear anywhere in Mojang's jar.

The master jar is Redstone Scope's `buildCarpetJar` output
(`redstone-scope/mod/versions/1.12.2-carpet/Kisde-Carpet-server-1.12.2-RedstoneScope.jar`), used straight from there
on this PC. Test-box and release runs get it fresh through `../box-inputs.json` (discord-bot/testbox.py `inputs`,
copied to `inputs/master-1.12.2.jar` in the run's copy); a jar older than Redstone Scope's code stops the
run/release. No hand-made copies (user 2026-10-30). Override with `-Pmaster=`.

## Per-version layer

Only data: `src/main/resources/useleess-carpet/minecraft/<version>.properties` (Mojang jar SHA-1, Mojang URLs, local
file names, main class) + `def minecraft` and the master jar path in `build.gradle`.

### Adding a Minecraft version

1. Add `<version>.properties` (SHA-1 + URL from Mojang's version manifest, piston-meta.mojang.com).
2. Build with `-Pmaster=<that version's master jar>` and the new `minecraft` value (one jar per version).
3. Add the version to `ingame-test.sh` checks if its files differ.

## Tests

- Unit (`src/test`, run by `gradlew build`, no game): `PatchTest` (delta, format, leak check), `InstallerTest`
  (download / own copy / cache / mirrors / no internet with a stand-in web), `HygieneTest` (language keys, no
  English in code, version data, no copied code blocks).
- In-game (`../ingame-test.sh`, test box): the real launcher jar on real servers: download from Mojang, contents,
  own copy, cache reuse, boot, one rule each of VasTech / Kisde / Bullet, Redstone Scope loaded, clean stop.
