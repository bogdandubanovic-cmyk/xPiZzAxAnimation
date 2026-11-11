package xpizzax.emoticons.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent output preferences for the cinematic renderer. */
public final class StudioExportSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SETTINGS_FILE = MinecraftClient.getInstance().runDirectory.toPath()
            .resolve("config").resolve("xpizzaxanimation").resolve("studio-export.json");

    public int fps = 60;
    public int width = 1920;
    public int height = 1080;
    public int bitrateMbps = 35;
    public String format = "mp4";
    public String quality = "high";
    public String outputDirectory = MinecraftClient.getInstance().runDirectory.toPath()
            .resolve("xpizzaxanimation-renders").toString();
    public String fileName = "xPiZzAx-cinematic";

    private static StudioExportSettings current = loadInternal();
    private StudioExportSettings() {}

    public static StudioExportSettings get() { return current; }
    public static void setFps(int value) {
        current.fps = Math.max(1, Math.min(240, value)); save();
    }
    public static void cycleFps() {
        int[] values = {30, 60, 120, 144, 240};
        int next = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current.fps) next = (i + 1) % values.length;
        current.fps = values[next]; save();
    }
    public static void cycleResolution() {
        if (current.width == 1920 && current.height == 1080) { current.width=2560; current.height=1440; }
        else if (current.width == 2560 && current.height == 1440) { current.width=3840; current.height=2160; }
        else { current.width=1920; current.height=1080; }
        save();
    }
    public static void cycleQuality() {
        current.quality = switch (current.quality) {
            case "high" -> "near-lossless";
            case "near-lossless" -> "balanced";
            default -> "high";
        };
        current.bitrateMbps = current.quality.equals("near-lossless") ? 80
                : current.quality.equals("balanced") ? 16 : 35;
        save();
    }
    public static void chooseOutputDirectory() {
        JFileChooser chooser = new JFileChooser(current.outputDirectory);
        chooser.setDialogTitle("Choose cinematic export folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            current.outputDirectory = chooser.getSelectedFile().getAbsolutePath();
            save();
        }
    }
    public static Path outputFile() {
        String safeName = current.fileName == null || current.fileName.isBlank()
                ? "xPiZzAx-cinematic" : current.fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        return Path.of(current.outputDirectory).resolve(safeName + "." + current.format);
    }
    public static void save() {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            Files.writeString(SETTINGS_FILE, GSON.toJson(current), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[xPiZzAxAnimation] Could not save export settings: " + e.getMessage());
        }
    }
    private static StudioExportSettings loadInternal() {
        try {
            if (Files.isRegularFile(SETTINGS_FILE)) {
                StudioExportSettings loaded = GSON.fromJson(Files.readString(SETTINGS_FILE, StandardCharsets.UTF_8),
                        StudioExportSettings.class);
                if (loaded != null) return loaded;
            }
        } catch (Exception e) {
            System.err.println("[xPiZzAxAnimation] Could not load export settings: " + e.getMessage());
        }
        return new StudioExportSettings();
    }
}
