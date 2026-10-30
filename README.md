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

Command line instead (hosts, panels): `java -Xmx2G -jar useleess-carpet-1.12.2-<version>.jar nogui` builds the
server jar and starts the server in the same window; it also makes `start-useleess-carpet.bat` /
`start-useleess-carpet.sh` and never changes your own scripts. From then on: that script, or
`java -Xmx2G -jar useleess-carpet-1.12.2-server.jar nogui`.

Java 8 is needed (like any 1.12.2 server).

No internet on the server? Put Mojang's official `minecraft_server.1.12.2.jar` next to the launcher; it is used
instead of downloading (checked by its fingerprint).

Need only the finished jar (for a host panel, CarpetSP or another tool), without starting the server? It is
written to `useleess-carpet-1.12.2-server.jar`, or to the file you name:

```
java -jar useleess-carpet-1.12.2-<version>.jar --install-only my-carpet-server.jar
```

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
