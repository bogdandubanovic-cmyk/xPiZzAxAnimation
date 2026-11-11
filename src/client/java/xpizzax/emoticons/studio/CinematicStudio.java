package xpizzax.emoticons.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import xpizzax.emoticons.animation.Rig;
import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;
import net.minecraft.util.math.Vec3d;

import javax.swing.JFileChooser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Own recording and editing state. ReplayMod is deliberately not accessed or controlled. */
public final class CinematicStudio {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = MinecraftClient.getInstance().runDirectory.toPath()
            .resolve("config").resolve("xpizzaxanimation").resolve("studio");
    private static StudioProject project = new StudioProject();
    private static boolean recording;
    private static boolean livePreview;
    private static int selectedFrame = -1;
    private static long lastSampleTick = Long.MIN_VALUE;

    private CinematicStudio() {}

    public static boolean isRecording() { return recording; }
    public static boolean isLivePreview() { return livePreview; }
    public static void toggleLivePreview() { livePreview = !livePreview; }
    public static StudioProject project() { return project; }
    public static int selectedFrameIndex() { return selectedFrame; }
    public static StudioFrame selectedFrame() {
        if (selectedFrame < 0 || selectedFrame >= project.frames.size()) return null;
        return project.frames.get(selectedFrame);
    }
    public static StudioFrame previewAt(float tick) { return StudioInterpolator.sample(project, tick); }
    
    /** Applies editable bone tracks only to the local player's xPiZzAx rig preview. */
    public static void applyToRig(Rig rig, LivingEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!livePreview || client.player == null || entity != client.player) return;
        StudioFrame selected = selectedFrame();
        if (selected == null) return;
        StudioFrame pose = StudioInterpolator.sample(project, selected.tick);
        if (pose == null) return;
        for (var entry : pose.bones.entrySet()) {
            Rig.Bone bone = rig.bones.get(entry.getKey());
            if (bone == null) continue;
            StudioFrame.BonePose value = entry.getValue();
            bone.transform.rotate.set((float)Math.toRadians(value.pitch),
                    (float)Math.toRadians(value.yaw), (float)Math.toRadians(value.roll));
            bone.transform.translate.set(value.x, value.y, value.z);
            bone.transform.scale.set(value.scaleX, value.scaleY, value.scaleZ);
        }
    }

    public static void toggleRecording(MinecraftClient client) {
        if (recording) stopRecording(); else startRecording();
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
        if (!project.frames.isEmpty()) {
            project.frames.get(0).keyframe = true;
            project.frames.get(project.frames.size() - 1).keyframe = true;
            selectFrame(project.frames.size() - 1);
        }
        save();
    }

    /** One world-tick sample. This does not change camera or player controls. */
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
        VanillaEmoticonsBridge.captureStudioPose(player, frame);
        project.frames.add(frame);
        project.durationTicks = frame.tick;
    }

    public static void selectFrame(int index) {
        if (project.frames.isEmpty()) { selectedFrame = -1; return; }
        selectedFrame = Math.max(0, Math.min(project.frames.size() - 1, index));
    }

    public static void addPoseKeyframe() {
        StudioFrame frame = selectedFrame();
        if (frame == null) return;
        frame.keyframe = true;
        save();
    }

    public static void deleteSelectedFrame() {
        if (selectedFrame < 0 || selectedFrame >= project.frames.size()) return;
        project.frames.remove(selectedFrame);
        for (int i = 0; i < project.frames.size(); i++) {
            project.frames.get(i).tick = i;
        }
        project.durationTicks = Math.max(0, project.frames.size() - 1);
        selectFrame(Math.min(selectedFrame, project.frames.size() - 1));
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
            case "camera_x" -> frame.cameraX += amount;
            case "camera_y" -> frame.cameraY += amount;
            case "camera_z" -> frame.cameraZ += amount;
            case "camera_yaw" -> frame.cameraYaw += amount;
            case "camera_pitch" -> frame.cameraPitch = Math.max(-89F, Math.min(89F, frame.cameraPitch + amount));
            case "camera_fov" -> frame.cameraFov = Math.max(20F, Math.min(110F, frame.cameraFov + amount));
            default -> { return; }
        }
        frame.keyframe = true;
        save();
    }

    public static void nudgeBone(String boneName, String axis, float amount) {
        StudioFrame frame = selectedFrame();
        if (frame == null) return;
        StudioFrame.BonePose pose = frame.bones.computeIfAbsent(boneName, key -> new StudioFrame.BonePose());
        switch (axis) {
            case "pitch" -> pose.pitch += amount;
            case "yaw" -> pose.yaw += amount;
            case "roll" -> pose.roll += amount;
            default -> { return; }
        }
        frame.keyframe = true;
        save();
    }

    public static void setExpression(String expression) {
        StudioFrame frame = selectedFrame();
        if (frame == null) return;
        frame.eyes.expression = expression;
        frame.keyframe = true;
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

    public static boolean saveProjectAs() {
        JFileChooser chooser = new JFileChooser(DIRECTORY.toFile());
        chooser.setDialogTitle("Save xPiZzAxAnimation project");
        chooser.setSelectedFile(DIRECTORY.resolve("cinematic.xpa").toFile());
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return false;
        Path file = chooser.getSelectedFile().toPath();
        if (!file.getFileName().toString().toLowerCase().endsWith(".xpa"))
            file = file.resolveSibling(file.getFileName() + ".xpa");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, GSON.toJson(project), StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            System.err.println("[xPiZzAxAnimation] Could not save project: " + exception.getMessage());
            return false;
        }
    }

    public static boolean openProject() {
        JFileChooser chooser = new JFileChooser(DIRECTORY.toFile());
        chooser.setDialogTitle("Open xPiZzAxAnimation project");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("xPiZzAxAnimation project (*.xpa)", "xpa"));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return false;
        return loadProject(chooser.getSelectedFile().toPath());
    }

    public static boolean loadLastProject() { return loadProject(DIRECTORY.resolve("last-project.xpa")); }

    private static boolean loadProject(Path file) {
        if (!Files.isRegularFile(file)) return false;
        try {
            StudioProject loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), StudioProject.class);
            if (loaded == null || loaded.formatVersion != 1 || loaded.frames == null) return false;
            loaded.frames.sort(Comparator.comparingInt(f -> f.tick));
            project = loaded;
            selectedFrame = project.frames.isEmpty() ? -1 : 0;
            return true;
        } catch (Exception exception) {
            System.err.println("[xPiZzAxAnimation] Could not load project: " + exception.getMessage());
            return false;
        }
    }
}
