package xpizzax.emoticons.animation;

import java.util.Arrays;
import net.minecraft.entity.LivingEntity;

/** Interruptible locomotion fades. No pose snapshots or allocations per draw. */
final class PrimaryActionBlend {
    private ActionPlayback[] actions = new ActionPlayback[0];
    private float[] previous = new float[0];
    private float[] weights = new float[0];
    private boolean[] started = new boolean[0];
    private int selected = -1;

    void setup(ActionPlayback... actions) {
        this.actions = actions;
        this.previous = new float[actions.length];
        this.weights = new float[actions.length];
        this.started = new boolean[actions.length];
        this.selected = -1;
    }

    void select(ActionPlayback action) {
        for (int i = 0; i < this.actions.length; i++) {
            if (this.actions[i] != action) continue;
            if (i == this.selected) return;
            this.selected = i;
            // Returning to walk during a fade must resume its step, not restart
            // the same first frame for every short W/A/S/D press. Actual one-shot
            // actions retain their rewind behavior, including secondary jumps.
            if (!this.started[i] || !action.isLooping()) action.rewind();
            this.started[i] = true;
            action.fadeIn();
            return;
        }
    }

    void finishTransition() {
        Arrays.fill(this.previous, 0F);
        Arrays.fill(this.weights, 0F);
        if (this.selected >= 0) {
            this.previous[this.selected] = this.weights[this.selected] = 1F;
        }
    }

    void update() {
        if (this.selected < 0) return;
        System.arraycopy(this.weights, 0, this.previous, 0, this.weights.length);
        float old = this.weights[this.selected];
        float fade = this.actions[this.selected].config.fade;
        float next = !Float.isFinite(fade) || fade <= 0F ? 1F : Math.min(1F, old + 1F / fade);
        float remaining = old < 1F ? (1F - next) / (1F - old) : 0F;
        for (int i = 0; i < this.actions.length; i++) {
            this.weights[i] = i == this.selected ? next : this.weights[i] * remaining;
            // Advance a fading-out clip for its last interpolated frame too.
            // Each visible clip advances once, regardless of how often the
            // player/model is rendered or movement direction changes.
            if (this.actions[i] != null && (this.previous[i] > 0F || this.weights[i] > 0F)) {
                this.actions[i].update();
            }
        }
    }

    void apply(LivingEntity entity, Rig rig, float delta) {
        float total = 0F;
        for (int i = 0; i < this.actions.length; i++) {
            float weight = this.previous[i] + (this.weights[i] - this.previous[i]) * delta;
            if (weight <= 0F || this.actions[i] == null) continue;
            total += weight;
            // Accumulated normalized blending retains the entire current mix
            // when a new transition interrupts it, instead of replacing it with
            // an unblended lastActive pose. Weights add up to one.
            this.actions[i].apply(entity, rig, delta, weight / total, false);
        }
    }
}
