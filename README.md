# xPiZzAxAnimation

Fabric client mod for **Minecraft 1.20.4** / **Java 17**.

This repository contains the source project. It preserves the player animation and eye customization features, plus optional ReplayMod and Cars integrations. ReplayMod itself is not bundled in the mod; it remains an optional mod installed separately by the player.

## Build locally

1. Install a 64-bit Java 17 JDK.
2. Open this folder in a terminal.
3. On Windows run `gradlew.bat --no-daemon remapJar`; on Linux/macOS run `./gradlew --no-daemon remapJar`.
4. The distributable is `build/libs/xPiZzAxAnimation.jar`.

The ReplayMod 1.20.4-2.6.15 JAR is required only as a compile-time API for the Replay bridge. The GitHub Actions workflow downloads that exact official build into the temporary CI workspace; it is not committed to this repository or bundled into xPiZzAxAnimation. The Cars compile-only API is already in `libs/`.

## GitHub builds and releases

Every push to `main` and every pull request builds the mod and uploads a short-lived CI artifact. To publish a player-facing release after a successful build, push a version tag, for example:

```bash
git tag v1.0.3
git push origin v1.0.2
```

The workflow builds the JAR and attaches `xPiZzAxAnimation.jar` to the GitHub Release. Players should download the JAR from the **Releases** page, not the source-code ZIP.

## Automatic in-game updating

GitHub Releases provides the update source, but **automatic in-game updating is not enabled by this workflow alone**. The mod still needs a client-side update checker and a safe post-exit installer. Older installed builds that do not contain that updater cannot update themselves remotely; they need one manual bridge/update installation first.
