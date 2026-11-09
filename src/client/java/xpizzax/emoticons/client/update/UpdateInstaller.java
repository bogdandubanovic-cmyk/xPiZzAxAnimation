package xpizzax.updater;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Standalone updater. It waits until Minecraft exits, then installs exactly
 * one xPiZzAxAnimation JAR in the game's mods directory.
 */
public final class UpdateInstaller {
    private static final String MOD_JAR_NAME = "xPiZzAxAnimation.jar";

    private UpdateInstaller() {}

    public static void main(String[] args) {
        if (args.length != 6) System.exit(2);

        long pid = Long.parseLong(args[0]);
        Path modsDirectory = Path.of(args[1]).toAbsolutePath().normalize();
        Path staged = Path.of(args[2]).toAbsolutePath().normalize();
        String modId = args[3];
        String version = args[4];
        Path log = Path.of(args[5]).toAbsolutePath().normalize();

        try {
            Files.createDirectories(log.getParent());
            writeLog(log, "Updater started; waiting for Minecraft PID " + pid);
            writeLog(log, "Target mods directory: " + modsDirectory);
            writeLog(log, "Staged update: " + staged);

            while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                Thread.sleep(500);
            }

            writeLog(log, "Minecraft closed; installing " + version);
            installIntoMods(modsDirectory, staged, modId, version, log);
        } catch (Exception error) {
            writeLog(log, "FAILED: " + error);
            System.exit(3);
        }
    }

    private static void installIntoMods(
            Path modsDirectory, Path staged, String modId, String version, Path log
    ) throws Exception {
        Files.createDirectories(modsDirectory);
        if (!Files.isDirectory(modsDirectory) || !Files.isWritable(modsDirectory)) {
            throw new IOException("Mods directory is not writable: " + modsDirectory);
        }
        if (!isValidJar(staged, modId, version)) {
            throw new IOException("Staged JAR failed identity/version validation: " + staged);
        }

        Path target = modsDirectory.resolve(MOD_JAR_NAME);
        if (Files.exists(target) && !isValidModJar(target, modId)) {
            throw new IOException("Refusing to overwrite a different file: " + target);
        }

        List<Path> oldModJars = findInstalledModJars(modsDirectory, modId);
        Path backupDirectory = modsDirectory.resolve(".xpa-update-backup-" + ProcessHandle.current().pid());
        if (Files.exists(backupDirectory)) deleteTree(backupDirectory);
        Files.createDirectories(backupDirectory);

        Map<Path, Path> backups = new LinkedHashMap<>();
        try {
            // Move every existing copy of this mod aside. No other mod is touched.
            for (Path oldJar : oldModJars) {
                Path backup = backupDirectory.resolve(oldJar.getFileName());
                move(oldJar, backup);
                backups.put(oldJar, backup);
                writeLog(log, "Removed old mod from active mods folder: " + oldJar.getFileName());
            }

            // One canonical filename prevents accumulating versioned JARs in /mods.
            move(staged, target);
            if (!isValidJar(target, modId, version)) {
                throw new IOException("Installed JAR validation failed: " + target);
            }

            writeLog(log, "SUCCESS: installed " + version + " at " + target);

            // Old copies are deleted only after the new JAR has passed validation.
            try {
                deleteTree(backupDirectory);
            } catch (Exception cleanupError) {
                writeLog(log, "WARNING: could not remove temporary backup folder: " + cleanupError);
            }

            // Remove only stale staged copies of this mod from config/.../updates.
            cleanupStagedCopies(staged.getParent(), modId, log);
        } catch (Exception installError) {
            writeLog(log, "Install failed; restoring previous mod JARs: " + installError);
            try {
                Files.deleteIfExists(target);
                for (Map.Entry<Path, Path> backup : backups.entrySet()) {
                    if (Files.exists(backup.getValue())) {
                        Files.deleteIfExists(backup.getKey());
                        move(backup.getValue(), backup.getKey());
                    }
                }
                deleteTree(backupDirectory);
            } catch (Exception rollbackError) {
                installError.addSuppressed(rollbackError);
                writeLog(log, "ROLLBACK FAILED: " + rollbackError);
            }
            throw installError;
        }
    }

    private static List<Path> findInstalledModJars(Path modsDirectory, String modId) throws IOException {
        List<Path> matches = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(modsDirectory)) {
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(file) && name.endsWith(".jar") && isValidModJar(file, modId)) {
                    matches.add(file);
                }
            }
        }
        return matches;
    }

    private static void cleanupStagedCopies(Path updatesDirectory, String modId, Path log) {
        if (updatesDirectory == null || !Files.isDirectory(updatesDirectory)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(updatesDirectory)) {
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(file) && name.endsWith(".jar") && isValidModJar(file, modId)) {
                    try {
                        Files.deleteIfExists(file);
                        writeLog(log, "Deleted stale staged copy: " + file.getFileName());
                    } catch (Exception cleanupError) {
                        writeLog(log, "WARNING: could not delete staged copy " + file + ": " + cleanupError);
                    }
                }
            }
        } catch (Exception error) {
            writeLog(log, "WARNING: could not clean updates directory: " + error);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static boolean isValidModJar(Path file, String modId) {
        if (!Files.isRegularFile(file)) return false;
        try (JarFile jar = new JarFile(file.toFile())) {
            var entry = jar.getEntry("fabric.mod.json");
            if (entry == null) return false;
            String json;
            try (var in = jar.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String idPattern = "\\\"id\\\"\\s*:\\s*\\\""
                    + Pattern.quote(modId) + "\\\"";
            return Pattern.compile(idPattern).matcher(json).find();
        } catch (Exception error) {
            return false;
        }
    }

    private static boolean isValidJar(Path file, String modId, String version) {
        if (!isValidModJar(file, modId)) return false;
        try (JarFile jar = new JarFile(file.toFile())) {
            var entry = jar.getEntry("fabric.mod.json");
            String json;
            try (var in = jar.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String versionPattern = "\\\"version\\\"\\s*:\\s*\\\""
                    + Pattern.quote(version) + "\\\"";
            return Pattern.compile(versionPattern).matcher(json).find();
        } catch (Exception error) {
            return false;
        }
    }

    private static void writeLog(Path file, String message) {
        try {
            Files.writeString(file, "[" + java.time.Instant.now() + "] " + message
                    + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }
}
