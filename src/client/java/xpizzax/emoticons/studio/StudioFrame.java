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

    public static final class BonePose {
        public float x, y, z;
        public float pitch, yaw, roll;
        public float scaleX = 1F, scaleY = 1F, scaleZ = 1F;
    }

    public static final class EyePose {
        public float lookYaw, lookPitch;
        public float openness = 1F;
        public String expression = "normal";
    }
}
