# xPiZzAxAnimation

Fabric client mod for **Minecraft 1.20.4** / **Java 17**.

This repository contains player animations, animated eyes, customization, and optional ReplayMod/Cars integrations.

## Build

Run `gradlew.bat --no-daemon remapJar` on Windows or `./gradlew --no-daemon remapJar` on Linux/macOS. The JAR is written to `build/libs/xPiZzAxAnimation.jar`.

ReplayMod is downloaded by GitHub Actions as a compile-only API; it is not bundled into the mod.

## Releases

Push a tag such as `v1.0.4`. GitHub Actions builds the remapped JAR and publishes it as the `xPiZzAxAnimation.jar` asset in a GitHub Release. Normal pushes to `main` create temporary CI artifacts only.

## Automatic updates

New builds check the latest stable GitHub Release at startup, download the matching JAR, validate its Fabric ID/version and SHA-256 digest (when available), and schedule replacement after Minecraft exits. The game never overwrites a loaded JAR.

**One-time bootstrap for legacy installs:** versions such as 1.0.2 do not contain an update checker and cannot update themselves. Put `INSTALL_UPDATE_BRIDGE.bat` and `INSTALL_UPDATE_BRIDGE.ps1` into the instance's `mods` folder and run the BAT once with Minecraft closed. It downloads the latest release directly from GitHub and replaces the old mod. Future releases install automatically after closing Minecraft.
