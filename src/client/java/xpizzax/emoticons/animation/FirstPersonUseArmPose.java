package xpizzax.emoticons.animation;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Keep a native eating/drinking arm in the camera frame while the body bends. */
public final class FirstPersonUseArmPose {
    private final Matrix4f pose = new Matrix4f();
    private final Vector3f pivot = new Vector3f();
    private final Vector3f euler = new Vector3f();

    /** Arm: pivot XYZ in pixels, then pitch/yaw/roll in radians. */
    public void compensate(Matrix4fc inverseUpper, Matrix4fc inverseRoot, float[] arm) {
        // Body rendering later applies root * upper. Undo those transforms only
        // for this arm, rather than aiming it at the hidden animated head.
        pose.set(inverseUpper).mul(inverseRoot)
                .translate(arm[0] / 16F, arm[1] / 16F, arm[2] / 16F)
                .rotateZYX(arm[5], arm[4], arm[3]);
        pose.getTranslation(pivot);
        pose.getEulerAnglesZYX(euler);
        arm[0] = pivot.x * 16F; arm[1] = pivot.y * 16F; arm[2] = pivot.z * 16F;
        arm[3] = euler.x; arm[4] = euler.y; arm[5] = euler.z;
    }
}
