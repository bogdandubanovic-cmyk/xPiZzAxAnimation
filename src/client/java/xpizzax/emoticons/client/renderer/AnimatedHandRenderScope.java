package xpizzax.emoticons.client.renderer;

import java.util.ArrayDeque;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import xpizzax.emoticons.client.RpmAccessoryHelper;

/** Keep the physical left wrist through legacy item hooks and display changes. */
public final class AnimatedHandRenderScope implements AutoCloseable {
    private static final class State {
        final ArrayDeque<AnimatedHandRenderScope> active = new ArrayDeque<>();
        final ArrayDeque<AnimatedHandRenderScope> pool = new ArrayDeque<>();
    }
    private static final ThreadLocal<State> STATES = ThreadLocal.withInitial(State::new);
    private final State owner;
    private final MatrixStack grip = new MatrixStack();
    private ItemStack item;
    private boolean open;
    private boolean nativeBound;

    private AnimatedHandRenderScope(State owner) { this.owner = owner; }

    public static AnimatedHandRenderScope begin(LivingEntity entity, ItemStack item,
            Arm arm, MatrixStack base, ModelWithArms vanillaModel) {
        State state = STATES.get();
        boolean pose = VanillaEmoticonsBridge.hasPose(entity);
        // OFF is an exact comparison with the original RPM/vanilla hand path.
        boolean offhand = arm == entity.getMainArm().getOpposite();
        boolean bodyAccessory = offhand
                ? BackAccessoryRenderScope.isBodyAccessory(
                        entity, item)
                : item != null && !item.isEmpty() && RpmAccessoryHelper.isBodyAccessory(item, false);
        boolean capture = pose && arm == Arm.LEFT && item != null && !item.isEmpty()
                && !bodyAccessory;
        if (!capture && state.active.isEmpty()) return null;
        AnimatedHandRenderScope scope = state.pool.pollFirst();
        if (scope == null) scope = new AnimatedHandRenderScope(state);
        scope.item = null;
        scope.nativeBound = false;
        // The caller has already applied the torso bend and child-model scale.
        // Snapshot before a legacy hook can replace the arm with fixed rotations.
        if (capture) {
            scope.grip.peek().getPositionMatrix().set(base.peek().getPositionMatrix());
            scope.grip.peek().getNormalMatrix().set(base.peek().getNormalMatrix());
            boolean bound = pose && VanillaEmoticonsBridge.bindHand(entity, scope.grip, arm);
            if (bound) {
                scope.grip.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90F));
                scope.grip.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180F));
                scope.grip.translate(-1F / 16F, 0.125F, -0.625F);
                scope.item = item;
            }
        }
        // A nested draw for another arm/item must mask the outer hand scope,
        // even when both hands contain equal copies of the same item.
        scope.open = true;
        state.active.push(scope);
        return scope;
    }

    /** Vanilla has already applied the posed arm and our forearm bend. */
    public static void markNativeBinding(ItemStack item, Arm arm) {
        AnimatedHandRenderScope scope = STATES.get().active.peek();
        if (scope != null && scope.item != null && arm == Arm.LEFT
                && ItemStack.areEqual(scope.item, item)) scope.nativeBound = true;
    }

    public static void apply(ItemStack item, ModelTransformationMode mode, MatrixStack matrices) {
        AnimatedHandRenderScope scope = STATES.get().active.peek();
        if (scope == null || scope.item == null || scope.nativeBound || (mode != ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                && mode != ModelTransformationMode.THIRD_PERSON_RIGHT_HAND)
                || !ItemStack.areEqual(scope.item, item)) return;
        // Repair only a renderer which skipped ModelWithArms.setArmAngle.
        // The native path keeps its exact left-hand display, handedness and
        // any later item-specific transforms; it must never be replaced here.
        matrices.peek().getPositionMatrix().set(scope.grip.peek().getPositionMatrix());
        matrices.peek().getNormalMatrix().set(scope.grip.peek().getNormalMatrix());
    }

    @Override public void close() {
        if (!this.open) return;
        this.owner.active.pop();
        this.open = false;
        this.item = null;
        this.nativeBound = false;
        this.owner.pool.addFirst(this);
    }
}
