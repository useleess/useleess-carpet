# Useleess Carpet changelog

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
