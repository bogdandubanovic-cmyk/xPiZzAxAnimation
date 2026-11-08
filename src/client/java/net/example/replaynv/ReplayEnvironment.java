package net.example.replaynv;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.events.ReplayClosedCallback;
import com.replaymod.replay.events.ReplayClosingCallback;
import com.replaymod.replay.events.ReplayOpenedCallback;
import com.replaymod.replaystudio.replay.ReplayFile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Session state survives world recreation during seeks and ReplayMod rendering. */
public final class ReplayEnvironment {
    private static final String ENTRY = "xpizzax/environment.properties";
    private static final EventRegistrations EVENTS = new EventRegistrations();
    private static ReplayFile replayFile;
    private static ReplayEnvironmentOptions options = ReplayEnvironmentOptions.RECORDED;
    private static ClientWorld lastWorld;
    private static boolean dirty, registered, saveFailed;

    private ReplayEnvironment() {}
    public static void register() {
        if (registered) return;
        registered = true;
        EVENTS.on(ReplayOpenedCallback.EVENT, handler -> open(handler.getReplayFile()));
        // ReplayHandler saves the archive after this callback, then closes it.
        EVENTS.on(ReplayClosingCallback.EVENT, handler -> {
            if (handler.getReplayFile() == replayFile) savePending();
        });
        EVENTS.on(ReplayClosedCallback.EVENT, handler -> {
            if (handler.getReplayFile() == replayFile) clear();
        });
        EVENTS.register();
    }
    private static void open(ReplayFile file) {
        replayFile = file;
        options = ReplayEnvironmentOptions.RECORDED;
        dirty = false;
        saveFailed = false;
        lastWorld = null;
        try {
            var saved = file.get(ENTRY);
            if (saved.isPresent()) {
                try (InputStream stream = saved.get()) {
                    byte[] bytes = stream.readNBytes(16385);
                    if (bytes.length > 16384) throw new IOException("Environment entry is too large");
                    try (var reader = new InputStreamReader(new ByteArrayInputStream(bytes),
                            StandardCharsets.UTF_8)) {
                        options = ReplayEnvironmentOptions.read(reader);
                    }
                }
            }
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("[xPiZzAxAnimation] Cannot read time/weather: " + error.getMessage());
        }
        updateLegacyDayFlag();
    }
    public static void tick(MinecraftClient client) {
        if (!ReplayState.isReplayOpen()) {
            if (replayFile != null) clear();
            return;
        }
        ReplayFile currentFile = ReplayModReplay.instance.getReplayHandler().getReplayFile();
        if (currentFile != replayFile) open(currentFile);
        // Also clear the read scope if another mod cancelled tickTime after its HEAD injections.
        if (client.world != null && (Object) client.world.getLevelProperties() instanceof ReplayTimeAccess access)
            access.xpizzax$setAdvancingTime(false);
        if (client.world != null && client.world != lastWorld) {
            lastWorld = client.world;
            refreshLighting(client);
        }
    }
    private static void clear() {
        replayFile = null;
        lastWorld = null;
        dirty = false;
        saveFailed = false;
        options = ReplayEnvironmentOptions.RECORDED;
        updateLegacyDayFlag();
    }
    public static ReplayEnvironmentOptions options() { return options; }
    /** Vertex opacity only. Never feed this value into weather/fog gradients. */
    public static float rainOpacity() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && replayFile != null && ReplayState.isReplayOpen()
                ? options.rainOpacity() : 1F;
    }
    public static ReplayEnvironmentOptions optionsFor(World world) {
        MinecraftClient client = MinecraftClient.getInstance();
        return world instanceof ClientWorld && client.world == world && replayFile != null
                && ReplayState.isReplayOpen() ? options : ReplayEnvironmentOptions.RECORDED;
    }
    public static ReplayEnvironmentOptions optionsForProperties(Object properties) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && client.world.getLevelProperties() == properties
                ? optionsFor(client.world) : ReplayEnvironmentOptions.RECORDED;
    }
    public static void setOptions(ReplayEnvironmentOptions value) {
        if (replayFile == null || !ReplayState.isReplayOpen() || options.equals(value)) return;
        options = value;
        dirty = true;
        saveFailed = false;
        updateLegacyDayFlag();
        refreshLighting(MinecraftClient.getInstance());
    }
    private static void updateLegacyDayFlag() {
        XPizzaXReplay.isAlwaysDayEnabled = options.fixedTime() && options.timeTicks() == 6000;
    }
    private static void refreshLighting(MinecraftClient client) {
        if (client.world == null) return;
        client.world.calculateAmbientDarkness();
        if (client.gameRenderer != null) client.gameRenderer.getLightmapTextureManager().tick();
        // Slider changes do not rebuild chunks or reload the world renderer.
    }
    /** Stage a tiny entry; ReplayMod saves the archive on normal replay exit. */
    public static boolean savePending() {
        if (replayFile == null || !dirty) return !saveFailed;
        try (var writer = new OutputStreamWriter(replayFile.write(ENTRY), StandardCharsets.UTF_8)) {
            options.write(writer);
        } catch (IOException error) {
            saveFailed = true;
            System.err.println("[xPiZzAxAnimation] Cannot save time/weather: " + error.getMessage());
            return false;
        }
        dirty = false;
        saveFailed = false;
        return true;
    }
}
