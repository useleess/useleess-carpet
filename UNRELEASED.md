# Unreleased changes

Collected here until the next release, then used as the GitHub / Modrinth notes.

## Fixed

- Double-clicking the jar could stop with "Something went wrong: AccessDeniedException: C:\WINDOWS\system32\.useleess-carpet-temp":
  Windows sometimes starts a double-clicked jar in its system folder, and the installer worked there. It now always
  works in the folder the jar is in.
