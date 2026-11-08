package xpizzax.emoticons.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import xpizzax.unified.ui.ThemeColor;

/** Both controls share one persistent file; changing one preserves the other. */
public final class AnimationPreferences {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("rpmmodels.properties");
    private static final Properties VALUES = load();
    private static boolean body = Boolean.parseBoolean(VALUES.getProperty("bodyAnimation", "true"));
    private static boolean eyes = Boolean.parseBoolean(VALUES.getProperty("animatedEyes", "true"));
    private static int theme = ThemeColor.parse(VALUES.getProperty("uiColor")).orElse(ThemeColor.DEFAULT);
    private static boolean themeDirty;
    private AnimationPreferences() {}
    private static Properties load() {
        Properties p = new Properties();
        if (Files.isRegularFile(FILE)) try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) { p.load(r); }
        catch (IOException e) { System.err.println("[xPiZzAxAnimation] Could not read animation settings: " + e); }
        return p;
    }
    public static boolean bodyEnabled() { return body; }
    public static boolean eyesEnabled() { return eyes; }
    public static int themeColor() { return theme; }
    /** Dragging changes only memory; mouse release or closing the drawer commits it. */
    public static void previewThemeColor(int rgb) {
        rgb &= 0xFFFFFF;
        if (theme != rgb) { theme=rgb; themeDirty=true; }
    }
    public static void flushThemeColor() { if(themeDirty) save(); }
    public static void setBodyEnabled(boolean enabled) { if (body != enabled) { body = enabled; save(); } }
    public static void setEyesEnabled(boolean enabled) { if (eyes != enabled) { eyes = enabled; save(); } }
    private static void save() {
        VALUES.setProperty("bodyAnimation", Boolean.toString(body));
        VALUES.setProperty("animatedEyes", Boolean.toString(eyes));
        VALUES.setProperty("uiColor", ThemeColor.hex(theme));
        try {
            Files.createDirectories(FILE.toAbsolutePath().getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { VALUES.store(w, "xPiZzAxAnimation"); }
            try { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING); }
            themeDirty=false;
        } catch (IOException e) { System.err.println("[xPiZzAxAnimation] Could not save animation settings: " + e); }
    }
}
