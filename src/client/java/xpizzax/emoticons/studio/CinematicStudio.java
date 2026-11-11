package xpizzax.emoticons.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import xpizzax.emoticons.animation.Rig;
import xpizzax.emoticons.client.renderer.OptionalMods;
import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;
import xpizzax.emoticons.mixin.client.CinematicStudioCameraAccessor;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Standalone take recorder and simple BBS-style in-world editing state. */
public final class CinematicStudio {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = MinecraftClient.getInstance().runDirectory.toPath()
            .resolve("config").resolve("xpizzaxanimation").resolve("studio");
    private static final Set<Integer> CAMERA_KEYS = new HashSet<>();
    private static final Map<String, StudioFrame.ModelPose> LATEST_EMOTE_POSE = new HashMap<>();

    private static StudioProject project = new StudioProject();
    private static boolean recording;
    private static boolean livePreview;
    private static boolean cameraPreview;
    private static boolean previewPlaying;
    private static boolean studioOpen;
    private static boolean freeCamera;
    private static float previewTick;
    private static long lastPreviewWorldTick = Long.MIN_VALUE;
    private static long lastSampleTick = Long.MIN_VALUE;
    private static long lastEmoteCaptureTick = Long.MIN_VALUE;
    private static int selectedFrame = -1;
    private static double freeX, freeY, freeZ;
    private static float freeYaw, freePitch;
    private static Perspective previousPerspective;

    private CinematicStudio() {}

    public static boolean isRecording() { return recording; }
    public static boolean isStudioOpen() { return studioOpen; }
    public static boolean isFreeCamera() { return freeCamera; }
    public static boolean isLivePreview() { return livePreview; }
    public static void toggleLivePreview() { livePreview = !livePreview; }
    public static boolean isCameraPreview() { return cameraPreview; }
    public static void toggleCameraPreview() { cameraPreview = !cameraPreview; }
    public static boolean isPreviewPlaying() { return previewPlaying; }
    public static float previewTick() { return previewTick; }
    public static StudioProject project() { return project; }
    public static int selectedFrameIndex() { return selectedFrame; }
    public static StudioFrame selectedFrame() {
        if (selectedFrame < 0 || selectedFrame >= project.frames.size()) return null;
        return project.frames.get(selectedFrame);
    }
    public static StudioFrame previewAt(float tick) { return StudioInterpolator.sample(project, tick); }

    /** Enter the studio and place a separate camera in front of the local player. */
    public static void enterStudio(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) return;
        if (!studioOpen) {
            previousPerspective = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            float bodyYaw = client.player.getYaw();
            double radians = Math.toRadians(bodyYaw);
            freeX = client.player.getX() - Math.sin(radians) * 4.5D;
            freeY = client.player.getEyeY() - 0.25D;
            freeZ = client.player.getZ() + Math.cos(radians) * 4.5D;
            freeYaw = bodyYaw + 180F;
            freePitch = 0F;
        }
        studioOpen = true;
        freeCamera = true;
        livePreview = true;
        cameraPreview = false;
        CAMERA_KEYS.clear();
        ensureInitialFrame(client);
    }

    public static void leaveStudio(MinecraftClient client) {
        studioOpen = false;
        freeCamera = false;
        CAMERA_KEYS.clear();
        if (client != null && previousPerspective != null) {
            client.options.setPerspective(previousPerspective);
        }
        previousPerspective = null;
    }

    private static void ensureInitialFrame(MinecraftClient client) {
        if (!project.frames.isEmpty()) {
            if (selectedFrame < 0) selectFrame(0);
            return;
        }
        StudioFrame frame = new StudioFrame();
        frame.tick = 0;
        frame.x = client.player.getX(); frame.y = client.player.getY(); frame.z = client.player.getZ();
        frame.bodyYaw = client.player.bodyYaw;
        frame.headYaw = client.player.headYaw;
        frame.headPitch = client.player.getPitch();
        frame.cameraX = freeX; frame.cameraY = freeY; frame.cameraZ = freeZ;
        frame.cameraYaw = freeYaw; frame.cameraPitch = freePitch;
        VanillaEmoticonsBridge.captureStudioPose(client.player, frame);
        project.frames.add(frame);
        project.durationTicks = 0;
        selectFrame(0);
    }

    /** Called by the screen for WASD/Space/Shift. */
    public static void cameraKey(int key, boolean pressed) {
        if (!studioOpen || !freeCamera) return;
        if (pressed) CAMERA_KEYS.add(key); else CAMERA_KEYS.remove(key);
    }

    /** Right-drag camera look, independent from the player's view controls. */
    public static void rotateFreeCamera(double dx, double dy) {
        if (!studioOpen || !freeCamera) return;
        freeYaw = wrapDegrees(freeYaw + (float)dx * 0.22F);
        freePitch = Math.max(-89F, Math.min(89F, freePitch + (float)dy * 0.22F));
    }

    private static void updateFreeCamera() {
        if (!studioOpen || !freeCamera) return;
        float yaw = (float)Math.toRadians(freeYaw);
        float pitch = (float)Math.toRadians(freePitch);
        double forwardX = -Math.sin(yaw) * Math.cos(pitch);
        double forwardY = -Math.sin(pitch);
        double forwardZ = Math.cos(yaw) * Math.cos(pitch);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double dx = 0, dy = 0, dz = 0;
        if (CAMERA_KEYS.contains(GLFW.GLFW_KEY_W)) { dx += forwardX; dy += forwardY; dz += forwardZ; }
        if (CAMERA_KEYS.contains(GLFW.GLFW_KEY_S)) { dx -= forwardX; dy -= forwardY; dz -= forwardZ; }
        if (CAMERA_KEYS.contains(GLFW.GLFW_KEY_D)) { dx -= rightX; dz -= rightZ; }
        if (CAMERA_KEYS.contains(GLFW.GLFW_KEY_A)) { dx += rightX; dz += rightZ; }
        if (CAMERA_KEYS.contains(GLFW.GLFW_KEY_SPACE)) dy += 1;
        if (CAMERA_KEYS.contains(GLFW.GLFW_KEY_LEFT_SHIFT)) dy -= 1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0.0001D) {
            double speed = CAMERA_KEYS.contains(GLFW.GLFW_KEY_LEFT_CONTROL) ? 0.72D : 0.22D;
            freeX += dx / length * speed;
            freeY += dy / length * speed;
            freeZ += dz / length * speed;
        }
    }

    private static float wrapDegrees(float angle) {
        angle %= 360F;
        if (angle >= 180F) angle -= 360F;
        if (angle < -180F) angle += 360F;
        return angle;
    }

    /** Camera override is deliberately independent of ReplayMod and its playback pipeline. */
    public static void applyCamera(Camera camera) {
        if (freeCamera && studioOpen) {
            CinematicStudioCameraAccessor accessor = (CinematicStudioCameraAccessor)(Object)camera;
            accessor.xpizzax$setPos(freeX, freeY, freeZ);
            accessor.xpizzax$setRotation(freeYaw, freePitch);
            return;
        }
        if (!cameraPreview) return;
        StudioFrame selected = selectedFrame();
        if (selected == null) return;
        StudioFrame pose = StudioInterpolator.sample(project, previewPlaying ? previewTick : selected.tick);
        if (pose == null) return;
        CinematicStudioCameraAccessor accessor = (CinematicStudioCameraAccessor)(Object)camera;
        accessor.xpizzax$setPos(pose.cameraX, pose.cameraY, pose.cameraZ);
        accessor.xpizzax$setRotation(pose.cameraYaw, pose.cameraPitch);
    }

    /** Applies editable rig bones to the local player's xPiZzAx model preview. */
    public static void applyToRig(Rig rig, LivingEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!studioOpen || !livePreview || client.player == null || entity != client.player) return;
        StudioFrame selected = selectedFrame();
        if (selected == null) return;
        StudioFrame pose = StudioInterpolator.sample(project, previewPlaying ? previewTick : selected.tick);
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

    /** Capture Emotecraft's final PlayerEntityModel transforms after its animation layer ran. */
    public static void captureEmotecraftPose(LivingEntity entity, PlayerEntityModel<?> model) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!recording || client.player == null || entity != client.player
                || !(entity instanceof AbstractClientPlayerEntity player)
                || !OptionalMods.isEmotecraftPlaying(player)) return;
        LATEST_EMOTE_POSE.clear();
        capturePart(model, "head", model.head);
        capturePart(model, "hat", model.hat);
        capturePart(model, "body", model.body);
        capturePart(model, "jacket", model.jacket);
        capturePart(model, "right_arm", model.rightArm);
        capturePart(model, "left_arm", model.leftArm);
        capturePart(model, "right_leg", model.rightLeg);
        capturePart(model, "left_leg", model.leftLeg);
        capturePart(model, "right_sleeve", model.rightSleeve);
        capturePart(model, "left_sleeve", model.leftSleeve);
        capturePart(model, "right_pants", model.rightPants);
        capturePart(model, "left_pants", model.leftPants);
        lastEmoteCaptureTick = client.world == null ? -1L : client.world.getTime();
    }

    private static void capturePart(PlayerEntityModel<?> model, String name, ModelPart part) {
        StudioFrame.ModelPose pose = new StudioFrame.ModelPose();
        pose.pivotX = part.pivotX; pose.pivotY = part.pivotY; pose.pivotZ = part.pivotZ;
        pose.pitch = part.pitch; pose.yaw = part.yaw; pose.roll = part.roll;
        pose.scaleX = part.xScale; pose.scaleY = part.yScale; pose.scaleZ = part.zScale;
        LATEST_EMOTE_POSE.put(name, pose);
    }

    /** Reapply a recorded Emotecraft model pose while the studio preview is active. */
    public static void applyCapturedEmotecraftPose(LivingEntity entity, PlayerEntityModel<?> model) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!studioOpen || recording || !livePreview || client.player == null || entity != client.player) return;
        StudioFrame selected = selectedFrame();
        if (selected == null) return;
        StudioFrame frame = StudioInterpolator.sample(project, previewPlaying ? previewTick : selected.tick);
        if (frame == null || !frame.emotecraftPose) return;
        applyPart(model.head, frame.emotecraftModel.get("head"));
        applyPart(model.hat, frame.emotecraftModel.get("hat"));
        applyPart(model.body, frame.emotecraftModel.get("body"));
        applyPart(model.jacket, frame.emotecraftModel.get("jacket"));
        applyPart(model.rightArm, frame.emotecraftModel.get("right_arm"));
        applyPart(model.leftArm, frame.emotecraftModel.get("left_arm"));
        applyPart(model.rightLeg, frame.emotecraftModel.get("right_leg"));
        applyPart(model.leftLeg, frame.emotecraftModel.get("left_leg"));
        applyPart(model.rightSleeve, frame.emotecraftModel.get("right_sleeve"));
        applyPart(model.leftSleeve, frame.emotecraftModel.get("left_sleeve"));
        applyPart(model.rightPants, frame.emotecraftModel.get("right_pants"));
        applyPart(model.leftPants, frame.emotecraftModel.get("left_pants"));
    }

    private static void applyPart(ModelPart part, StudioFrame.ModelPose pose) {
        if (pose == null) return;
        part.setPivot(pose.pivotX, pose.pivotY, pose.pivotZ);
        part.pitch = pose.pitch; part.yaw = pose.yaw; part.roll = pose.roll;
        part.xScale = pose.scaleX; part.yScale = pose.scaleY; part.zScale = pose.scaleZ;
    }

    public static void toggleRecording(MinecraftClient client) {
        if (recording) stopRecording(); else startRecording();
    }

    public static void startRecording() {
        project = new StudioProject();
        project.name = "Cinematic " + System.currentTimeMillis();
        selectedFrame = -1;
        lastSampleTick = Long.MIN_VALUE;
        lastEmoteCaptureTick = Long.MIN_VALUE;
        LATEST_EMOTE_POSE.clear();
        recording = true;
        previewPlaying = false;
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

    /** One world-tick sample. Emotecraft transforms are copied from the renderer mixin. */
    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.isPaused()) return;
        updateFreeCamera();
        long now = client.world.getTime();
        if (previewPlaying && now != lastPreviewWorldTick) {
            lastPreviewWorldTick = now;
            previewTick += 1F;
            if (previewTick >= project.durationTicks) {
                previewTick = project.durationTicks;
                previewPlaying = false;
                selectedFrame = project.frames.size() - 1;
            }
        }
        if (!recording || now == lastSampleTick) return;
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
        frame.cameraX = freeCamera ? freeX : pos.x;
        frame.cameraY = freeCamera ? freeY : pos.y;
        frame.cameraZ = freeCamera ? freeZ : pos.z;
        frame.cameraYaw = freeCamera ? freeYaw : camera.getYaw();
        frame.cameraPitch = freeCamera ? freePitch : camera.getPitch();
        VanillaEmoticonsBridge.captureStudioPose(player, frame);
        if (lastEmoteCaptureTick >= 0 && now - lastEmoteCaptureTick <= 2
                && !LATEST_EMOTE_POSE.isEmpty()) {
            frame.emotecraftPose = true;
            LATEST_EMOTE_POSE.forEach((name, pose) -> frame.emotecraftModel.put(name, copy(pose)));
        }
        project.frames.add(frame);
        project.durationTicks = frame.tick;
    }

    private static StudioFrame.ModelPose copy(StudioFrame.ModelPose source) {
        StudioFrame.ModelPose p = new StudioFrame.ModelPose();
        p.pivotX=source.pivotX; p.pivotY=source.pivotY; p.pivotZ=source.pivotZ;
        p.pitch=source.pitch; p.yaw=source.yaw; p.roll=source.roll;
        p.scaleX=source.scaleX; p.scaleY=source.scaleY; p.scaleZ=source.scaleZ;
        return p;
    }

    public static void selectFrame(int index) {
        if (project.frames.isEmpty()) { selectedFrame = -1; previewTick = 0F; return; }
        selectedFrame = Math.max(0, Math.min(project.frames.size() - 1, index));
        previewTick = project.frames.get(selectedFrame).tick;
        previewPlaying = false;
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
        for (int i = 0; i < project.frames.size(); i++) project.frames.get(i).tick = i;
        project.durationTicks = Math.max(0, project.frames.size() - 1);
        selectFrame(Math.min(selectedFrame, project.frames.size() - 1));
        save();
    }

    public static void togglePreviewPlayback() {
        if (project.frames.isEmpty()) return;
        if (previewPlaying) {
            previewPlaying = false;
            int nearest = 0;
            for (int i = 1; i < project.frames.size(); i++)
                if (Math.abs(project.frames.get(i).tick-previewTick) < Math.abs(project.frames.get(nearest).tick-previewTick)) nearest = i;
            selectFrame(nearest);
        } else {
            if (selectedFrame() == null || previewTick >= project.durationTicks) previewTick = 0F;
            previewPlaying = true;
            lastPreviewWorldTick = Long.MIN_VALUE;
        }
    }

    /** Edit a selected rig bone in rotation, translation or scale mode. */
    public static void editBone(String boneName, String mode, String axis, float amount) {
        StudioFrame frame = selectedFrame();
        if (frame == null) return;
        StudioFrame.BonePose pose = frame.bones.computeIfAbsent(boneName, key -> new StudioFrame.BonePose());
        switch (mode) {
            case "move" -> {
                switch (axis) { case "x" -> pose.x += amount; case "y" -> pose.y += amount; case "z" -> pose.z += amount; default -> { return; } }
            }
            case "scale" -> {
                switch (axis) {
                    case "x" -> pose.scaleX = clamp(pose.scaleX + amount, 0.05F, 4F);
                    case "y" -> pose.scaleY = clamp(pose.scaleY + amount, 0.05F, 4F);
                    case "z" -> pose.scaleZ = clamp(pose.scaleZ + amount, 0.05F, 4F);
                    default -> { return; }
                }
            }
            default -> {
                switch (axis) {
                    case "x" -> pose.pitch = clamp(pose.pitch + amount, -180F, 180F);
                    case "y" -> pose.yaw = clamp(pose.yaw + amount, -180F, 180F);
                    case "z" -> pose.roll = clamp(pose.roll + amount, -180F, 180F);
                    default -> { return; }
                }
            }
        }
        frame.keyframe = true;
    }

    public static void nudgeSelected(String channel, float amount) {
        StudioFrame frame = selectedFrame();
        if (frame == null) return;
        switch (channel) {
            case "body_yaw" -> { frame.bodyYaw += amount; frame.bones.computeIfAbsent("body", key -> new StudioFrame.BonePose()).yaw += amount; }
            case "head_yaw" -> { frame.headYaw += amount; frame.eyes.lookYaw += amount; frame.bones.computeIfAbsent("head", key -> new StudioFrame.BonePose()).yaw += amount; }
            case "head_pitch" -> { frame.headPitch += amount; frame.eyes.lookPitch += amount; frame.bones.computeIfAbsent("head", key -> new StudioFrame.BonePose()).pitch += amount; }
            case "eye_yaw" -> frame.eyes.lookYaw = clamp(frame.eyes.lookYaw + amount, -90F, 90F);
            case "eye_pitch" -> frame.eyes.lookPitch = clamp(frame.eyes.lookPitch + amount, -60F, 60F);
            case "eye_open" -> frame.eyes.openness = clamp(frame.eyes.openness + amount, 0F, 1F);
            case "camera_x" -> frame.cameraX += amount;
            case "camera_y" -> frame.cameraY += amount;
            case "camera_z" -> frame.cameraZ += amount;
            case "camera_yaw" -> frame.cameraYaw += amount;
            case "camera_pitch" -> frame.cameraPitch = clamp(frame.cameraPitch + amount, -89F, 89F);
            case "camera_fov" -> frame.cameraFov = clamp(frame.cameraFov + amount, 20F, 110F);
            default -> { return; }
        }
        frame.keyframe = true;
        save();
    }

    public static void nudgeBone(String boneName, String axis, float amount) {
        editBone(boneName, "rotate", axis, amount);
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

    /** Open the directory containing saved .xpa takes without blocking Minecraft. */
    public static void openProjectDirectory() {
        try {
            Files.createDirectories(DIRECTORY);
            if (!java.awt.Desktop.isDesktopSupported()) return;
            new Thread(() -> {
                try { java.awt.Desktop.getDesktop().open(DIRECTORY.toFile()); }
                catch (Exception error) {
                    System.err.println("[xPiZzAxAnimation] Could not open takes folder: " + error);
                }
            }, "xpa-open-takes-folder").start();
        } catch (IOException error) {
            System.err.println("[xPiZzAxAnimation] Could not create takes folder: " + error);
        }
    }

    /** File pickers run outside Minecraft's render thread to avoid client freezes/kicks. */
    public static void saveProjectAs() {
        final String snapshot = GSON.toJson(project);
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser chooser = new JFileChooser(DIRECTORY.toFile());
                chooser.setDialogTitle("Save xPiZzAxAnimation project");
                chooser.setSelectedFile(DIRECTORY.resolve("cinematic.xpa").toFile());
                if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;
                Path file = chooser.getSelectedFile().toPath();
                if (!file.getFileName().toString().toLowerCase().endsWith(".xpa"))
                    file = file.resolveSibling(file.getFileName() + ".xpa");
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.writeString(file, snapshot, StandardCharsets.UTF_8);
            } catch (Exception exception) {
                System.err.println("[xPiZzAxAnimation] Could not save project: " + exception);
            }
        });
    }

    public static void openProject() {
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser chooser = new JFileChooser(DIRECTORY.toFile());
                chooser.setDialogTitle("Open xPiZzAxAnimation project");
                chooser.setFileFilter(new FileNameExtensionFilter("xPiZzAxAnimation project (*.xpa)", "xpa"));
                if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
                Path selected = chooser.getSelectedFile().toPath();
                MinecraftClient.getInstance().execute(() -> loadProject(selected));
            } catch (Exception exception) {
                System.err.println("[xPiZzAxAnimation] Could not open project chooser: " + exception);
            }
        });
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
            previewTick = project.frames.isEmpty() ? 0F : project.frames.get(0).tick;
            previewPlaying = false;
            return true;
        } catch (Exception exception) {
            System.err.println("[xPiZzAxAnimation] Could not load project: " + exception.getMessage());
            return false;
        }
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
