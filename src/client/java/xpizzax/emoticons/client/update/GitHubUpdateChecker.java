package xpizzax.emoticons.client.update;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import xpizzax.updater.UpdateInstaller;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.jar.JarFile;

/**
 * Checks the public update manifest (GitHub Releases for now), downloads and
 * verifies the new JAR during Minecraft's startup resource reload, then
 * starts a standalone Java installer which replaces it after this JVM exits.
 */
public final class GitHubUpdateChecker {
    private static final String REPO = "bogdandubanovic-cmyk/xPiZzAxAnimation";
    private static final String MOD_ID = "xpizzaxanimation";
    private static final String ASSET = "xPiZzAxAnimation.jar";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private GitHubUpdateChecker() {}

    /**
     * @return true only when a verified update is ready and the external
     * installer has successfully started.
     */
    public static boolean checkAndPrepareUpdate() {
        try {
            var mod = FabricLoader.getInstance().getModContainer(MOD_ID);
            if (mod.isEmpty()) {
                log("Installed mod container not found; startup continues.");
                return false;
            }

            String installed = mod.get().getMetadata().getVersion().getFriendlyString();
            XpaUpdateStatus.show("Проверка обновлений xPiZzAxAnimation...", 0.0f);

            HttpRequest manifestRequest = HttpRequest.newBuilder(URI.create(
                    "https://api.github.com/repos/" + REPO + "/releases/latest"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "xPiZzAxAnimation-Updater")
                    .GET().build();

            HttpResponse<String> manifestResponse = HTTP.send(
                    manifestRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (manifestResponse.statusCode() != 200) {
                log("Update manifest request returned HTTP " + manifestResponse.statusCode());
                return false;
            }

            JsonObject release = JsonParser.parseString(manifestResponse.body()).getAsJsonObject();
            if (release.has("draft") && release.get("draft").getAsBoolean()) return false;
            if (release.has("prerelease") && release.get("prerelease").getAsBoolean()) return false;

            String tag = release.get("tag_name").getAsString();
            String latest = stripV(tag);
            if (compareVersions(latest, installed) <= 0) {
                log("Already up to date. Installed=" + installed + ", latest=" + latest);
                return normalizeInstalledModIfNeeded(installed, mod.get());
            }

            JsonObject asset = null;
            JsonArray assets = release.getAsJsonArray("assets");
            if (assets != null) {
                for (JsonElement element : assets) {
                    JsonObject candidate = element.getAsJsonObject();
                    if (ASSET.equals(candidate.get("name").getAsString())) {
                        asset = candidate;
                        break;
                    }
                }
            }
            if (asset == null) {
                log("Release " + tag + " does not contain " + ASSET);
                return false;
            }

            // Stage downloads in config, but always install the active mod into this
            // Minecraft instance's actual mods directory.
            Path modsDirectory = FabricLoader.getInstance().getGameDir()
                    .toAbsolutePath().normalize().resolve("mods");
            Files.createDirectories(modsDirectory);
            if (!Files.isDirectory(modsDirectory) || !Files.isWritable(modsDirectory)) {
                throw new IllegalStateException("Minecraft mods folder is not writable: " + modsDirectory);
            }
            log("Install destination (mods folder): " + modsDirectory);

            // Download the temporary payload directly inside /mods. It is NOT
            // a .jar yet, so Fabric cannot load it as a second mod.
            Path staged = modsDirectory.resolve(".xpa-update-" + latest + ".part");
            removeStaleStagedUpdates(modsDirectory, staged);
            Files.deleteIfExists(staged);

            XpaUpdateStatus.show("Загрузка xPiZzAxAnimation " + latest + "...", 0.0f);
            downloadAndReport(asset.get("browser_download_url").getAsString(),
                    asset.has("size") ? asset.get("size").getAsLong() : -1L, staged);

            String expectedDigest = asset.has("digest") && !asset.get("digest").isJsonNull()
                    ? asset.get("digest").getAsString() : "";
            if (expectedDigest.startsWith("sha256:")) {
                String actual = sha256(staged);
                if (!actual.equalsIgnoreCase(expectedDigest.substring("sha256:".length()))) {
                    Files.deleteIfExists(staged);
                    throw new IllegalStateException("SHA-256 mismatch; current mod was not touched");
                }
            }

            String downloadedVersion = readAndValidateMod(staged);
            if (compareVersions(downloadedVersion, latest) != 0) {
                Files.deleteIfExists(staged);
                throw new IllegalStateException("Downloaded mod version " + downloadedVersion
                        + " does not match release " + latest);
            }

            launchPortableInstaller(modsDirectory, staged, latest);
            XpaUpdateStatus.show("Обновление " + latest + " загружено. Подготовка установки...", 1.0f);
            log("Verified update " + latest + "; external installer started. Current=" + installed);
            return true;
        } catch (Exception error) {
            log("Update check failed safely: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
    }

    /**
     * On the first start after a legacy update, normalize the mod in /mods:
     * keep exactly one canonical xPiZzAxAnimation.jar and remove old duplicates.
     */
    private static boolean normalizeInstalledModIfNeeded(String installedVersion, ModContainer mod) throws Exception {
        Path modsDirectory = FabricLoader.getInstance().getGameDir()
                .toAbsolutePath().normalize().resolve("mods");
        if (!Files.isDirectory(modsDirectory)) return false;

        List<Path> modJars = new ArrayList<>();
        Path currentVersionJar = null;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(modsDirectory)) {
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!Files.isRegularFile(file) || !name.endsWith(".jar")) continue;
                try {
                    String fileVersion = readAndValidateMod(file);
                    modJars.add(file);
                    if (currentVersionJar == null
                            && compareVersions(fileVersion, installedVersion) == 0) {
                        currentVersionJar = file;
                    }
                } catch (Exception ignored) {
                    // Leave unrelated or malformed JARs alone.
                }
            }
        }

        Path canonical = modsDirectory.resolve(ASSET);
        if (modJars.size() == 1 && modJars.get(0).toAbsolutePath().normalize().equals(
                canonical.toAbsolutePath().normalize())) {
            return false;
        }
        // The currently loaded copy may itself have been launched from the old
        // config/updates location. Use Fabric's reported origin as a safe fallback.
        if (currentVersionJar == null) {
            currentVersionJar = resolveCurrentModJar(mod, installedVersion);
        }
        if (currentVersionJar == null) {
            log("No current-version xPiZzAxAnimation JAR could be resolved; skip normalization.");
            return false;
        }

        Path staged = modsDirectory.resolve(".xpa-update-normalize-" + installedVersion + ".part");
        removeStaleStagedUpdates(modsDirectory, staged);
        Files.copy(currentVersionJar, staged, StandardCopyOption.REPLACE_EXISTING);

        String copiedVersion = readAndValidateMod(staged);
        if (compareVersions(copiedVersion, installedVersion) != 0) {
            Files.deleteIfExists(staged);
            throw new IllegalStateException("Could not stage the currently installed mod for normalization");
        }

        launchPortableInstaller(modsDirectory, staged, installedVersion);
        XpaUpdateStatus.show("Исправление расположения xPiZzAxAnimation...", 1.0f);
        log("Prepared mods-folder normalization: " + modJars.size()
                + " matching JAR(s); canonical destination=" + canonical);
        return true;
    }

    private static Path resolveCurrentModJar(ModContainer mod, String installedVersion) {
        try {
            for (Path origin : mod.getOrigin().getPaths()) {
                Path candidate = origin.toAbsolutePath().normalize();
                if (!Files.isRegularFile(candidate)
                        || !candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }
                try {
                    if (compareVersions(readAndValidateMod(candidate), installedVersion) == 0) {
                        log("Resolved active mod from Fabric origin: " + candidate);
                        return candidate;
                    }
                } catch (Exception ignored) {
                    // Ignore origins that are not the active mod JAR.
                }
            }
        } catch (Exception error) {
            log("Could not resolve Fabric mod origin for normalization: " + error);
        }
        return null;
    }

    /**
     * Removes only obsolete temporary downloads created by this updater in /mods.
     */
    private static void removeStaleStagedUpdates(Path updates, Path keep) throws Exception {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(updates, ".xpa-update-*.part")) {
            for (Path file : files) {
                if (!file.toAbsolutePath().normalize().equals(keep.toAbsolutePath().normalize())) {
                    Files.deleteIfExists(file);
                    log("Removed stale staged update: " + file.getFileName());
                }
            }
        }
    }

    private static void downloadAndReport(String url, long total, Path destination) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(4))
                .header("User-Agent", "xPiZzAxAnimation-Updater")
                .GET().build();

        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            try (InputStream ignored = response.body()) {}
            throw new IllegalStateException("JAR download returned HTTP " + response.statusCode());
        }

        long copied = 0L;
        try (InputStream input = response.body();
             OutputStream output = Files.newOutputStream(destination,
                     StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            long lastReport = 0L;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                copied += read;
                if (copied - lastReport >= 128 * 1024 || (total > 0 && copied >= total)) {
                    float progress = total > 0 ? Math.min(1.0f, (float) copied / (float) total) : -1.0f;
                    String suffix = total > 0 ? " " + Math.round(progress * 100) + "%" : "";
                    XpaUpdateStatus.show("Загрузка обновления..." + suffix, progress);
                    lastReport = copied;
                }
            }
        } catch (Exception error) {
            Files.deleteIfExists(destination);
            throw error;
        }

        if (total > 0 && copied != total) {
            Files.deleteIfExists(destination);
            throw new IllegalStateException("Incomplete download: " + copied + "/" + total + " bytes");
        }
        if (copied < 32 * 1024) {
            Files.deleteIfExists(destination);
            throw new IllegalStateException("Downloaded JAR is unexpectedly small");
        }
    }

    private static String readAndValidateMod(Path jarPath) throws Exception {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry("fabric.mod.json");
            if (entry == null) throw new IllegalStateException("Downloaded JAR has no fabric.mod.json");
            JsonObject metadata;
            try (InputStreamReader reader = new InputStreamReader(
                    jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                metadata = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (!MOD_ID.equals(metadata.get("id").getAsString())) {
                throw new IllegalStateException("Downloaded JAR has wrong mod id");
            }
            return metadata.get("version").getAsString();
        }
    }

    private static void launchPortableInstaller(Path modsDirectory, Path staged, String version) throws Exception {
        Path config = FabricLoader.getInstance().getConfigDir().resolve("xpizzaxanimation");
        Path runtime = config.resolve("updater-runtime");
        Path classFile = runtime.resolve("xpizzax/updater/UpdateInstaller.class");
        Files.createDirectories(classFile.getParent());

        try (InputStream input = UpdateInstaller.class.getResourceAsStream("/xpizzax/updater/UpdateInstaller.class")) {
            if (input == null) throw new IllegalStateException("Embedded Java installer class is missing");
            Files.copy(input, classFile, StandardCopyOption.REPLACE_EXISTING);
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        Path logFile = config.resolve("update-installer.log");

        Process installer = new ProcessBuilder(javaExecutable.toString(), "-cp", runtime.toString(),
                "xpizzax.updater.UpdateInstaller",
                Long.toString(ProcessHandle.current().pid()),
                modsDirectory.toString(),
                staged.toString(),
                MOD_ID,
                version,
                logFile.toString(),
                FabricLoader.getInstance().getConfigDir().resolve("xpizzaxanimation").resolve("updates").toString())
                .redirectInput(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

        log("Standalone installer started; PID=" + installer.pid()
                + "; mods directory=" + modsDirectory);
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static int compareVersions(String first, String second) {
        String[] a = stripV(first).split("[-+]", 2)[0].split("\\.");
        String[] b = stripV(second).split("[-+]", 2)[0].split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? numericPart(a[i]) : 0;
            int bv = i < b.length ? numericPart(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int numericPart(String value) {
        String digits = value.replaceAll("[^0-9].*$", "");
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String stripV(String value) {
        return value.startsWith("v") ? value.substring(1) : value;
    }

    private static void log(String message) {
        System.out.println("[xPiZzAxAnimation Updater] " + message);
    }
}
