# Useleess Carpet

One Carpet server jar for Minecraft **1.12.2** with everything in it:

- **Carpet** (the original 1.12 Carpet mod)
- **Bullet** Carpet rules
- **Kisde** Carpet rules
- **VasTech** Carpet rules
- **Redstone Scope** (redstone debugger, server side)

## How to use

1. Download `useleess-carpet-1.12.2-<version>.jar` (the installer) from the releases page.
2. Put it in your server folder (a new empty folder, or your existing 1.12.2 server folder).
3. **Double-click it.** A window shows what it does:
   - it downloads Mojang's official 1.12.2 server jar **from Mojang** and builds the Carpet server jar
     `useleess-carpet-1.12.2-server.jar` in your server folder (temporary files, Mojang's jar included, are deleted);
   - it finds Java 8 on your PC (also the one the Minecraft launcher installs);
   - it makes its own start script, `start-useleess-carpet.bat`. Your own `start.bat` (or `run.sh`, ...) is never
     changed; it only tells you it still starts the old jar;
   - it asks you to accept Mojang's EULA;
   - it starts the server in its own window and deletes itself (it is not needed any more).
4. **From then on, double-click `start-useleess-carpet.bat`** (the window shows this in a big yellow box).

To update, put the new installer in the server folder and double-click it once: it rebuilds the server jar.

Command line instead (hosts, panels): `java -Xmx2G -XX:+UseG1GC -jar useleess-carpet-1.12.2-<version>.jar nogui` builds the
server jar and starts the server in the same window; it also makes `start-useleess-carpet.bat` /
`start-useleess-carpet.sh` and never changes your own scripts. From then on: that script, or
`java -Xmx2G -XX:+UseG1GC -jar useleess-carpet-1.12.2-server.jar nogui`.

Java 8 is needed (like any 1.12.2 server).

No internet on the server? Put Mojang's official `minecraft_server.1.12.2.jar` next to the launcher; it is used
instead of downloading (checked by its fingerprint).

Need only the finished jar (for a host panel, CarpetSP or another tool), without starting the server? It is
written to `useleess-carpet-1.12.2-server.jar`, or to the file you name:

```
java -jar useleess-carpet-1.12.2-<version>.jar --install-only my-carpet-server.jar
```

## Our own Carpet rules

Next to the rules of Carpet, Bullet, Kisde and VasTech, Useleess Carpet adds its own. They work like any other Carpet
rule: `/carpet <rule>`, `/carpet list`, `/carpet setDefault <rule> <value>` (saved in the world's `carpet.conf`) and
CarpetSP's rule screen.

| Rule | Default | What it does |
|---|---|---|
| `endPortalNoTeleportPlayer` | false | Players touching an end portal stay where they are; items, mobs and other entities still go through (end-portal farms keep working). |
| `customRecipes` | true | Ops can make their own crafting, furnace and brewing stand recipes with `/customrecipe` (see below). Off = saved recipes do not match and the command is refused; the recipe files stay. |
| `antiNoFall` | false | The server checks for itself that a player is standing on something before it ends their fall, so no-fall cheats (a client that always says it is on the ground) still take fall damage. Honest players notice nothing. |
| `litematicaAccuratePlacement` | false | Blocks placed by Litematica Upgraded's printer and Easy Place get the schematic's exact state (facing, half, open, a lever's switch), but never another block kind, other drops or redstone power. |
| `litematicaShulkerRestock` | false | Litematica Upgraded's printer and Easy Place may take the items they need out of shulker boxes in the player's inventory (the server moves them; vanilla cannot). |
| `redstoneScopeLogger` | false | Redstone Scope records block changes for its tools (sub-tick, waveform, probes, rewind). Off = no recording, no extra work and no Redstone Scope log lines; its commands say it is switched off. |

## Careful break for each player

Kisde Carpet's rule `carefulBreak` puts the blocks a player mines straight into that player's inventory. Useleess Carpet
lets every player choose when, without a new rule:

1. An op turns the rule on once: `/carpet carefulBreak true` (`/carpet setDefault carefulBreak true` keeps it).
2. Each player switches it on or off for themselves with `/log carefulBreak` (the same command again switches it off).
3. `/log carefulBreak sneak` (the default) = only while pressing the Sneak key; `/log carefulBreak always` = every
   block you mine, no Sneak key needed. Tab completion lists both.

The choice is saved per world (`useleess-carpet/careful-break.json`) and is back when the player joins after a server
restart. If something goes wrong the server tells the player once and carefulBreak works like in Kisde Carpet (Sneak
needed).

## Bots that sleep by themselves

A Carpet bot (made with `/player <name> spawn`) can go to bed by itself every night, so a server where only bots are
online still skips the night:

1. Put a bed next to the bot (within 3 blocks across and 2 up or down; Minecraft's own reach).
2. `/autosleep <bot> on` (anyone may use it; `/autosleep <bot> off` turns it off, `/autosleep <bot>` tells the state).

At night the bot lies down in the nearest free bed; Minecraft wakes it in the morning as usual. Like a player it can't
sleep with monsters close by, or in the Nether or the End. The choice is kept per world, also over a server restart.

## Making your own recipes (ops)

Type `/customrecipe` (operators, permission level 2; survival is fine, no creative mode and no mod on your side).
One chest window opens; your inventory is saved to a file and then used as the item picker, and given back exactly
when you leave.

1. Click an item in the picker (the bottom part of the window): it is selected. Use the category button (click = next,
   right-click = previous) and the page arrows to find it, or the sign (Search) to filter by name.
2. The button left of the output (crafting table / furnace / brewing stand; click = next, right-click = previous) picks
   the recipe type. Crafting uses the whole 3x3 grid; the furnace only the middle cell (what is smelted); the brewing
   stand the top middle cell (the ingredient) and the bottom middle cell (the bottle: a glass bottle or a potion; the
   potion type counts). Click a cell to put the selected item there (right-click empties the cell).
3. Select the result item and click the output slot (click again with the same item = one more, right-click = one less).
4. The repeater switches "any order" (shapeless) on or off. Click the emerald (Save): the recipe is saved under the
   result's name (`diamond_sword`, then `diamond_sword_2`). Save is refused with a message when the grid or result
   is empty or another recipe already gives a result for that grid.
5. Book (All recipes): load a saved recipe to change it (Save then overwrites it); shift-click one, then shift-click
   again, to delete it. Bucket (Clear) empties the grid and result. Barrier (Exit) or Esc closes the editor.

Recipes are saved in the world folder: `useleess-carpet/recipes/<name>.json` (shaped, shapeless, smelting or brewing;
items by registry name + meta (+ potion type), the result with its NBT). Players use them in any crafting table,
furnace or brewing stand (hoppers too) with a plain Minecraft client. A recipe that would clash with a vanilla one
(for example iron ore in the furnace) is refused. A broken
file is skipped with a note in the log and to ops; the others keep working.

## Why a launcher instead of a server jar?

A server jar contains Mojang's game code, which we may not hand out. This download contains **no Mojang code**: only
our patch, the changes Carpet, Bullet, Kisde, VasTech and Redstone Scope make. Your PC gets the game itself from
Mojang and applies the patch. Every file of the result is checked against its fingerprint, so a damaged download
can never start a broken server.

## Credits and licences

See [NOTICE.md](NOTICE.md). The patch contains code under the GNU LGPL 3.0 ([LICENSE](LICENSE),
[LICENSE-GPL](LICENSE-GPL)); this project is under the same licence.

- Bullet Carpet source: https://github.com/Dioswilson/Bullet-carpet
- Carpet 1.12 source: https://github.com/gnembon/carpetmod112

Not affiliated with or approved by Mojang or Microsoft.
