package xpizzax.emoticons.studio;

import java.util.LinkedHashMap;
import java.util.Map;

/** A sampled moment or editable keyframe in a cinematic project. Angles are degrees. */
public final class StudioFrame {
    public int tick;
    public boolean keyframe;
    public double x, y, z;
    public float bodyYaw, headYaw, headPitch;
    public double cameraX, cameraY, cameraZ;
    public float cameraYaw, cameraPitch, cameraFov = 70F;
    public final Map<String, BonePose> bones = new LinkedHashMap<>();
    public final EyePose eyes = new EyePose();

    /** Final vanilla player-model transforms captured while Emotecraft is active. */
    public boolean emotecraftPose;
    public final Map<String, ModelPose> emotecraftModel = new LinkedHashMap<>();

    public static final class BonePose {
        public float x, y, z;
        public float pitch, yaw, roll;
        public float scaleX = 1F, scaleY = 1F, scaleZ = 1F;
    }

    /** ModelPart transform in Minecraft model-space units. */
    public static final class ModelPose {
        public float pivotX, pivotY, pivotZ;
        public float pitch, yaw, roll;
        public float scaleX = 1F, scaleY = 1F, scaleZ = 1F;
    }

    public static final class EyePose {
        public float lookYaw, lookPitch;
        public float openness = 1F;
        public String expression = "normal";
    }
}
