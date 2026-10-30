# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

- The installer deletes itself once the server jar is built (on Windows right after the server stops): it is not
  needed any more. From then on start the server with `java -jar useleess-carpet-1.12.2-server.jar nogui`; the
  installer prints this command. To update, put the new installer in the server folder and start it once.
