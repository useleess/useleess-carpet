# Useleess Carpet

One Carpet server jar for Minecraft **1.12.2** with everything in it:

- **Carpet** (the original 1.12 Carpet mod)
- **Bullet** Carpet rules
- **Kisde** Carpet rules
- **VasTech** Carpet rules
- **Redstone Scope** (redstone debugger, server side)

## How to use

1. Download `useleess-carpet-1.12.2-<version>.jar` from the releases page.
2. Put it in your server folder and start it like a normal server jar:

   ```
   java -Xmx2G -jar useleess-carpet-1.12.2-<version>.jar nogui
   ```

3. The first start downloads Mojang's official 1.12.2 server jar **from Mojang**, builds the Carpet server jar on
   your PC and starts it. Later starts skip straight to the server.

Java 8 is needed (like any 1.12.2 server).

No internet on the server? Put Mojang's official `minecraft_server.1.12.2.jar` next to the launcher; it is used
instead of downloading (checked by its fingerprint).

Need the finished jar itself (for a host panel, CarpetSP or another tool)?

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
