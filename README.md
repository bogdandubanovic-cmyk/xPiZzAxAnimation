# xPiZzAxAnimation

Fabric client mod for **Minecraft 1.20.4** / **Java 17**.

Player animations, animated eyes, customization, and optional ReplayMod/Cars integrations.

## Build

On Windows, run `BUILD.bat` or:

```bat
gradlew.bat --no-daemon remapJar
```

On Linux/macOS:

```sh
./gradlew --no-daemon remapJar
```

The remapped mod is written to `build/libs/xPiZzAxAnimation.jar`.

ReplayMod is downloaded by GitHub Actions as a compile-only API and is not bundled into the mod. Player Animator and Bendy are included in the resulting JAR.

## Releases

Push a version tag such as `v1.0.15`. GitHub Actions builds the remapped JAR and publishes it as `xPiZzAxAnimation.jar` in a GitHub Release. Normal pushes to `main` create temporary CI artifacts only.

## Requirements

- Minecraft 1.20.4
- Java 17
- Fabric Loader
- Fabric API

ReplayMod and Cars integrations are optional.
