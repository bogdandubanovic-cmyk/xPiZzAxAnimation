package xpizzax.updater;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Standalone updater process. It is launched by the mod and waits for the
 * Minecraft JVM to exit before replacing the mod JAR. It has no launcher
 * dependency and uses only the Java runtime.
 */
public final class UpdateInstaller {
    private UpdateInstaller() {}

    public static void main(String[] args) {
        if (args.length != 6) {
            System.exit(2);
        }
        long pid = Long.parseLong(args[0]);
        Path target = Path.of(args[1]).toAbsolutePath().normalize();
        Path staged = Path.of(args[2]).toAbsolutePath().normalize();
        String modId = args[3];
        String version = args[4];
        Path log = Path.of(args[5]).toAbsolutePath().normalize();

        try {
            Files.createDirectories(log.getParent());
            writeLog(log, "Updater started; waiting for Minecraft PID " + pid);
            while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                Thread.sleep(500);
            }
            writeLog(log, "Minecraft closed; installing " + version);

            if (!isValidJar(staged, modId, version)) {
                throw new IOException("Staged JAR failed identity/version validation");
            }

            Path backup = target.resolveSibling(target.getFileName() + ".xpa-backup");
            IOException last = null;
            for (int attempt = 1; attempt <= 40; attempt++) {
                try {
                    if (!Files.isRegularFile(staged)) {
                        throw new IOException("Staged update is missing: " + staged);
                    }
                    Files.deleteIfExists(backup);
                    if (Files.exists(target)) {
                        move(target, backup);
                    }
                    try {
                        move(staged, target);
                        if (!isValidJar(target, modId, version)) {
                            throw new IOException("Installed JAR validation failed");
                        }
                        Files.deleteIfExists(backup);
                        writeLog(log, "SUCCESS: installed " + version + " at " + target);
                        return;
                    } catch (Exception installError) {
                        Files.deleteIfExists(target);
                        if (Files.exists(backup)) move(backup, target);
                        throw installError;
                    }
                } catch (Exception e) {
                    last = e instanceof IOException io ? io : new IOException(e);
                    writeLog(log, "Attempt " + attempt + "/40 failed: " + last);
                    Thread.sleep(1500);
                }
            }
            throw last == null ? new IOException("Update failed") : last;
        } catch (Exception e) {
            writeLog(log, "FAILED: " + e);
            System.exit(3);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isValidJar(Path file, String modId, String version) {
        if (!Files.isRegularFile(file)) return false;
        try (JarFile jar = new JarFile(file.toFile())) {
            var entry = jar.getEntry("fabric.mod.json");
            if (entry == null) return false;
            String json;
            try (var in = jar.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String idPattern = "\\\"id\\\"\\s*:\\s*\\\"" + Pattern.quote(modId) + "\\\"";
            String versionPattern = "\\\"version\\\"\\s*:\\s*\\\"" + Pattern.quote(version) + "\\\"";
            return Pattern.compile(idPattern).matcher(json).find()
                    && Pattern.compile(versionPattern).matcher(json).find();
        } catch (Exception e) {
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
