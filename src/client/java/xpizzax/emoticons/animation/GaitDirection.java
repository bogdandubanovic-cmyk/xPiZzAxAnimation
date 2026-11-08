package xpizzax.emoticons.animation;

/** Signed gait from real motion in the body's facing direction; no key/camera dependency. */
public final class GaitDirection {
    private boolean backwards;

    public float update(double dx, double dz, float bodyYaw) {
        double length = Math.hypot(dx, dz);
        if (Double.isFinite(length) && length > .01D && Float.isFinite(bodyYaw)) {
            double yaw = Math.toRadians(bodyYaw);
            double forward = (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / length;
            // Hysteresis keeps strafing/interpolation noise from changing the
            // direction every tick. Sideways movement uses the forward gait.
            if (forward < -.35D) this.backwards = true;
            else if (forward > -.15D) this.backwards = false;
        }
        // ActionPlayback keeps the current phase continuous when the sign
        // changes. Slowing the clock through zero instead holds the same pose
        // while the player is already walking in the opposite direction.
        return this.backwards ? -1F : 1F;
    }

    public void reset() { this.backwards = false; }
}
