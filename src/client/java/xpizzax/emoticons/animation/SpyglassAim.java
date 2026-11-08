package xpizzax.emoticons.animation;

/** Moves the vanilla eye/mouth aiming arm into the animated head's coordinate frame.
 * Poses contain pivot XYZ in pixels followed by pitch/yaw/roll in radians.
 * One instance per renderer keeps the render path allocation free. */
public final class SpyglassAim {
    private final double[] nativeRotation = new double[9];
    private final double[] animatedRotation = new double[9];
    private final double[] delta = new double[9];
    private final double[] armRotation = new double[9];
    private final double[] result = new double[9];

    public void align(float[] nativeHead, float[] animatedHead, float[] arm) {
        rotation(nativeHead, nativeRotation);
        rotation(animatedHead, animatedRotation);
        rotation(arm, armRotation);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            double value = 0;
            for (int k = 0; k < 3; k++) value += animatedRotation[row * 3 + k] * nativeRotation[col * 3 + k];
            delta[row * 3 + col] = value;
        }
        double x = arm[0] - nativeHead[0], y = arm[1] - nativeHead[1], z = arm[2] - nativeHead[2];
        for (int row = 0; row < 3; row++) {
            arm[row] = (float) (animatedHead[row] + delta[row * 3] * x + delta[row * 3 + 1] * y + delta[row * 3 + 2] * z);
            for (int col = 0; col < 3; col++) {
                double value = 0;
                for (int k = 0; k < 3; k++) value += delta[row * 3 + k] * armRotation[k * 3 + col];
                result[row * 3 + col] = value;
            }
        }
        double cosYaw = Math.hypot(result[7], result[8]);
        arm[4] = (float) Math.atan2(-result[6], cosYaw);
        if (cosYaw > 1.0E-7) {
            arm[3] = (float) Math.atan2(result[7], result[8]);
            arm[5] = (float) Math.atan2(result[3], result[0]);
        } else {
            arm[3] = 0;
            arm[5] = (float) Math.atan2(-result[1], result[4]);
        }
    }

    private static void rotation(float[] pose, double[] m) {
        double sx = Math.sin(pose[3]), cx = Math.cos(pose[3]);
        double sy = Math.sin(pose[4]), cy = Math.cos(pose[4]);
        double sz = Math.sin(pose[5]), cz = Math.cos(pose[5]);
        // ModelPart uses Rz(roll) * Ry(yaw) * Rx(pitch).
        m[0] = cz * cy; m[1] = cz * sy * sx - sz * cx; m[2] = cz * sy * cx + sz * sx;
        m[3] = sz * cy; m[4] = sz * sy * sx + cz * cx; m[5] = sz * sy * cx - cz * sx;
        m[6] = -sy; m[7] = cy * sx; m[8] = cy * cx;
    }
}
