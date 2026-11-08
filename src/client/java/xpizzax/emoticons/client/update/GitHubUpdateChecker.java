package xpizzax.emoticons.client.update;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarFile;

public final class GitHubUpdateChecker {
    private static final String REPO = "bogdandubanovic-cmyk/xPiZzAxAnimation";
    private static final String MOD_ID = "xpizzaxanimation";
    private static final String ASSET = "xPiZzAxAnimation.jar";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "xPiZzAxAnimation-Updater");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private GitHubUpdateChecker() {}

    public static void checkForUpdates() {
        EXECUTOR.execute(() -> {
            try {
                var mod = FabricLoader.getInstance().getModContainer(MOD_ID);
                if (mod.isEmpty()) return;
                String installed = mod.get().getMetadata().getVersion().getFriendlyString();

                HttpRequest req = HttpRequest.newBuilder(URI.create(
                        "https://api.github.com/repos/" + REPO + "/releases/latest"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "xPiZzAxAnimation-Updater")
                        .GET().build();
                HttpResponse<String> response = HTTP.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    log("No release available or GitHub returned HTTP " + response.statusCode());
                    return;
                }

                JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
                if (release.has("draft") && release.get("draft").getAsBoolean()) return;
                if (release.has("prerelease") && release.get("prerelease").getAsBoolean()) return;
                String tag = release.get("tag_name").getAsString();
                String latest = stripV(tag);
                if (compare(latest, installed) <= 0) {
                    log("Already up to date: " + installed);
                    return;
                }

                JsonObject asset = null;
                for (JsonElement e : release.getAsJsonArray("assets")) {
                    JsonObject candidate = e.getAsJsonObject();
                    if (ASSET.equals(candidate.get("name").getAsString())) {
                        asset = candidate;
                        break;
                    }
                }
                if (asset == null) {
                    log("Release " + tag + " has no " + ASSET);
                    return;
                }

                Path current = Path.of(GitHubUpdateChecker.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
                if (!Files.isRegularFile(current) || !current.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    log("Cannot locate installed JAR (development environment?)");
                    return;
                }
                Path staged = current.resolveSibling(current.getFileName() + ".xpa-update");
                Files.deleteIfExists(staged);

                HttpRequest download = HttpRequest.newBuilder(URI.create(
                        asset.get("browser_download_url").getAsString()))
                        .timeout(Duration.ofMinutes(3))
                        .header("User-Agent", "xPiZzAxAnimation-Updater").GET().build();
                HttpResponse<Path> file = HTTP.send(download, HttpResponse.BodyHandlers.ofFile(
                        staged, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
                if (file.statusCode() != 200) {
                    Files.deleteIfExists(staged);
                    log("Download failed: HTTP " + file.statusCode());
                    return;
                }

                String digest = asset.has("digest") && !asset.get("digest").isJsonNull()
                        ? asset.get("digest").getAsString() : "";
                if (digest.startsWith("sha256:") &&
                        !sha256(staged).equalsIgnoreCase(digest.substring(7))) {
                    Files.deleteIfExists(staged);
                    log("SHA-256 mismatch; keeping current version");
                    return;
                }

                try (JarFile jar = new JarFile(staged.toFile())) {
                    var entry = jar.getEntry("fabric.mod.json");
                    if (entry == null) throw new IllegalStateException("No fabric.mod.json");
                    JsonObject meta = JsonParser.parseReader(new InputStreamReader(
                            jar.getInputStream(entry), StandardCharsets.UTF_8)).getAsJsonObject();
                    if (!MOD_ID.equals(meta.get("id").getAsString()) ||
                            compare(meta.get("version").getAsString(), latest) != 0)
                        throw new IllegalStateException("Downloaded JAR identity/version mismatch");
                } catch (Exception invalid) {
                    Files.deleteIfExists(staged);
                    log("Invalid update JAR: " + invalid.getMessage());
                    return;
                }

                scheduleWindowsSwap(current, staged);
                log("Downloaded and verified " + tag + "; it will be installed after Minecraft closes.");
            } catch (Exception e) {
                log("Update check skipped safely: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private static void scheduleWindowsSwap(Path target, Path staged) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            log("Automatic replacement is currently configured for Windows only.");
            return;
        }

        Path dir = FabricLoader.getInstance().getConfigDir().resolve("xpizzaxanimation");
        Files.createDirectories(dir);
        Path script = dir.resolve("apply-update.ps1");
        Path logFile = dir.resolve("apply-update.log");

        // The helper is detached from Minecraft and waits for this exact game
        // process to exit. It then retries the replacement and keeps a rollback
        // copy until the new JAR is verified at the destination.
        String ps = """
                param(
                    [long]$WaitForProcessId,
                    [string]$Target,
                    [string]$Staged,
                    [string]$LogFile
                )
                $ErrorActionPreference = 'Stop'
                function Write-Log([string]$Message) {
                    try {
                        Add-Content -LiteralPath $LogFile -Value ("[{0}] {1}" -f (Get-Date -Format o), $Message)
                    } catch {}
                }

                Write-Log "Updater helper started. Waiting for Minecraft PID $WaitForProcessId."
                while ($true) {
                    $running = Get-Process -Id $WaitForProcessId -ErrorAction SilentlyContinue
                    if ($null -eq $running) { break }
                    Start-Sleep -Milliseconds 800
                }
                Write-Log "Minecraft process exited. Installing staged JAR."

                $Backup = $Target + '.xpa-backup'
                for ($Attempt = 1; $Attempt -le 30; $Attempt++) {
                    try {
                        if (!(Test-Path -LiteralPath $Staged -PathType Leaf)) {
                            throw "Staged update not found: $Staged"
                        }

                        if (Test-Path -LiteralPath $Backup) {
                            Remove-Item -LiteralPath $Backup -Force
                        }
                        if (Test-Path -LiteralPath $Target) {
                            Move-Item -LiteralPath $Target -Destination $Backup -Force
                        }

                        try {
                            Move-Item -LiteralPath $Staged -Destination $Target -Force
                            if (!(Test-Path -LiteralPath $Target -PathType Leaf)) {
                                throw 'New JAR was not created at target path.'
                            }
                            if ((Get-Item -LiteralPath $Target).Length -le 0) {
                                throw 'New JAR is empty.'
                            }
                            if (Test-Path -LiteralPath $Backup) {
                                Remove-Item -LiteralPath $Backup -Force
                            }
                            Write-Log "SUCCESS: update installed at $Target"
                            exit 0
                        }
                        catch {
                            if (Test-Path -LiteralPath $Target) {
                                Remove-Item -LiteralPath $Target -Force -ErrorAction SilentlyContinue
                            }
                            if (Test-Path -LiteralPath $Backup) {
                                Move-Item -LiteralPath $Backup -Destination $Target -Force -ErrorAction SilentlyContinue
                            }
                            throw
                        }
                    }
                    catch {
                        Write-Log ("Attempt {0}/30 failed: {1}" -f $Attempt, $_.Exception.Message)
                        Start-Sleep -Seconds 2
                    }
                }

                Write-Log "FAILED: could not install update after 30 attempts. Staged file was preserved."
                exit 3
                """;

        Files.writeString(script, ps, StandardCharsets.UTF_8);
        Process helper = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(),
                "-WaitForProcessId", Long.toString(ProcessHandle.current().pid()),
                "-Target", target.toString(),
                "-Staged", staged.toString(),
                "-LogFile", logFile.toString())
                .redirectInput(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        log("Update installer helper started (PID " + helper.pid() + "). It will replace the JAR after Minecraft exits.");
    }

    private static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(p)) {
            byte[] b = new byte[16384]; int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }
    private static String stripV(String s) { return s.startsWith("v") ? s.substring(1) : s; }
    private static int compare(String a, String b) {
        String[] x = stripV(a).split("[-+]", 2)[0].split("\\.");
        String[] y = stripV(b).split("[-+]", 2)[0].split("\\.");
        for (int i=0;i<Math.max(x.length,y.length);i++) {
            int xi=i<x.length?parse(x[i]):0, yi=i<y.length?parse(y[i]):0;
            if(xi!=yi)return Integer.compare(xi,yi);
        }
        return 0;
    }
    private static int parse(String s) {
        String d=s.replaceAll("[^0-9].*$","");
        try{return d.isEmpty()?0:Integer.parseInt(d);}catch(Exception e){return 0;}
    }
    private static void log(String s) { System.out.println("[xPiZzAxAnimation Updater] "+s); }
}
