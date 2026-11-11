package xpizzax.emoticons.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Own recording/project state for xPiZzAxAnimation.
 * It deliberately does not call, patch, or control ReplayMod.
 */
public final class CinematicStudio {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = MinecraftClient.getInstance().runDirectory.toPath()
            .resolve("config").resolve("xpizzaxanimation").resolve("studio");
    private static StudioProject project = new StudioProject();
    private static boolean recording;
    private static int selectedFrame = -1;
    private static long lastSampleTick = Long.MIN_VALUE;

    private CinematicStudio() {}

    public static boolean isRecording() { return recording; }
    public static StudioProject project() { return project; }
    public static int selectedFrameIndex() { return selectedFrame; }
    public static StudioFrame selectedFrame() {
        if (selectedFrame < 0 || selectedFrame >= project.frames.size()) return null;
        return project.frames.get(selectedFrame);
    }

    public static void toggleRecording(MinecraftClient client) {
        if (recording) stopRecording();
        else startRecording();
    }

    public static void startRecording() {
        project = new StudioProject();
        project.name = "Cinematic " + System.currentTimeMillis();
        selectedFrame = -1;
        lastSampleTick = Long.MIN_VALUE;
        recording = true;
    }

    public static void stopRecording() {
        if (!recording) return;
        recording = false;
        save();
    }

    /** Called from END_CLIENT_TICK; one sample is stored per game tick. */
    public static void tick(MinecraftClient client) {
        if (!recording || client.player == null || client.world == null || client.isPaused()) return;
        long now = client.world.getTime();
        if (now == lastSampleTick) return;
        lastSampleTick = now;
        PlayerEntity player = client.player;
        StudioFrame frame = new StudioFrame();
        frame.tick = project.frames.size();
        frame.x = player.getX(); frame.y = player.getY(); frame.z = player.getZ();
        frame.bodyYaw = player.bodyYaw;
        frame.headYaw = player.headYaw;
        frame.headPitch = player.getPitch();
        frame.eyes.lookYaw = player.headYaw - player.bodyYaw;
        frame.eyes.lookPitch = player.getPitch();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d pos = camera.getPos();
        frame.cameraX = pos.x; frame.cameraY = pos.y; frame.cameraZ = pos.z;
        frame.cameraYaw = camera.getYaw();
        frame.cameraPitch = camera.getPitch();
        project.frames.add(frame);
        project.durationTicks = frame.tick;
    }

    public static void selectFrame(int index) {
        if (project.frames.isEmpty()) { selectedFrame = -1; return; }
        selectedFrame = Math.max(0, Math.min(project.frames.size() - 1, index));
    }

    public static void addPoseKeyframe() {
        StudioFrame source = selectedFrame();
        if (source == null) return;
        StudioFrame copy = copyFrame(source);
        copy.tick = source.tick;
        project.frames.add(copy);
        project.frames.sort(Comparator.comparingInt(f -> f.tick));
        selectedFrame = project.frames.indexOf(copy);
        save();
    }

    public static void nudgeSelected(String channel, float amount) {
        StudioFrame frame = selectedFrame();
        if (frame == null) return;
        switch (channel) {
            case "body_yaw" -> frame.bodyYaw += amount;
            case "head_yaw" -> { frame.headYaw += amount; frame.eyes.lookYaw += amount; }
            case "head_pitch" -> { frame.headPitch += amount; frame.eyes.lookPitch += amount; }
            case "eye_yaw" -> frame.eyes.lookYaw += amount;
            case "eye_pitch" -> frame.eyes.lookPitch += amount;
            case "eye_open" -> frame.eyes.openness = Math.max(0F, Math.min(1F, frame.eyes.openness + amount));
            default -> { return; }
        }
        save();
    }

    public static void setExpression(String expression) {
        StudioFrame frame = selectedFrame();
        if (frame == null) return;
        frame.eyes.expression = expression;
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(DIRECTORY);
            Files.writeString(DIRECTORY.resolve("last-project.xpa"), GSON.toJson(project), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("[xPiZzAxAnimation] Could not save studio project: " + exception.getMessage());
        }
    }

    public static boolean loadLastProject() {
        Path file = DIRECTORY.resolve("last-project.xpa");
        if (!Files.isRegularFile(file)) return false;
        try {
            StudioProject loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), StudioProject.class);
            if (loaded == null || loaded.formatVersion != 1) return false;
            project = loaded;
            selectedFrame = project.frames.isEmpty() ? -1 : 0;
            return true;
        } catch (Exception exception) {
            System.err.println("[xPiZzAxAnimation] Could not load studio project: " + exception.getMessage());
            return false;
        }
    }

    private static StudioFrame copyFrame(StudioFrame source) {
        return GSON.fromJson(GSON.toJson(source), StudioFrame.class);
    }
}
