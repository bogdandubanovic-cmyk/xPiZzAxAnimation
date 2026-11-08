package xpizzax.emoticons.client.renderer;

import java.util.ArrayDeque;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import xpizzax.emoticons.client.RpmAccessoryHelper;

/**
 * Suppress the offhand copy of an item while the player renderer owns its
 * back-accessory pass. Classification is cached for the duration of one render,
 * and scope objects are reused to avoid per-frame garbage on busy servers.
 */
public final class BackAccessoryRenderScope {
    private static final class Context {
        AbstractClientPlayerEntity player;
        ItemStack offhand;
        ModelTransformationMode expectedMode;
        boolean bodyAccessory;
        boolean drawn;
        boolean allowed;

        void reset(AbstractClientPlayerEntity player) {
            this.player = player;
            this.offhand = player.getOffHandStack();
            this.bodyAccessory = !offhand.isEmpty() && RpmAccessoryHelper.isBodyAccessory(offhand);
            Arm off = player.getMainArm().getOpposite();
            this.expectedMode = off == Arm.LEFT
                    ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                    : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
            this.drawn = false;
            this.allowed = false;
        }

        void clear() {
            player = null;
            offhand = null;
            expectedMode = null;
            bodyAccessory = false;
            drawn = false;
            allowed = false;
        }
    }

    private static final class State {
        final ArrayDeque<Context> active = new ArrayDeque<>();
        final ArrayDeque<Context> free = new ArrayDeque<>();
    }

    private static final ThreadLocal<State> SCOPES = ThreadLocal.withInitial(State::new);
    private BackAccessoryRenderScope() {}

    public static void begin(AbstractClientPlayerEntity player) {
        State state = SCOPES.get();
        Context context = state.free.pollFirst();
        if (context == null) context = new Context();
        context.reset(player);
        state.active.push(context);
    }

    public static void end(AbstractClientPlayerEntity player) {
        State state = SCOPES.get();
        Context context = state.active.peek();
        if (context == null || context.player != player) return;
        state.active.pop();
        context.clear();
        state.free.addFirst(context);
    }

    /** Reuse the classification already calculated at player-render HEAD.
     * Held-item and accessory passes often ask about the same offhand stack. */
    public static boolean isBodyAccessory(LivingEntity player, ItemStack stack) {
        Context context = SCOPES.get().active.peek();
        if (context != null && context.player == player && stack != null
                && (stack == context.offhand || ItemStack.areEqual(stack, context.offhand))) {
            return context.bodyAccessory;
        }
        return stack != null && !stack.isEmpty() && RpmAccessoryHelper.isBodyAccessory(stack);
    }

    public static boolean beginAccessory(AbstractClientPlayerEntity player) {
        Context context = SCOPES.get().active.peek();
        if (context == null || context.player != player || !context.bodyAccessory || context.drawn) return false;
        context.drawn = true;
        context.allowed = true;
        return true;
    }

    public static void endAccessory(AbstractClientPlayerEntity player) {
        Context context = SCOPES.get().active.peek();
        if (context != null && context.player == player) context.allowed = false;
    }

    public static boolean shouldSuppress(ItemStack stack, ModelTransformationMode mode) {
        Context context = SCOPES.get().active.peek();
        return context != null && !context.allowed && context.bodyAccessory
                && mode == context.expectedMode && ItemStack.areEqual(stack, context.offhand);
    }
}
