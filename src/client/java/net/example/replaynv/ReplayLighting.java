package net.example.replaynv;
import java.lang.ref.WeakReference;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
/** Replay-owned light effect: never delete a player's original potion. */
public final class ReplayLighting {
    private static WeakReference<LivingEntity> owner = new WeakReference<>(null);
    private static StatusEffectInstance ownedEffect;
    private ReplayLighting() {}
    public static boolean enabled(MinecraftClient client) {
        return XPizzaXReplay.isNightVisionEnabled && ReplayState.isReplayOpen()
                && client.player != null && client.world != null;
    }
    public static void tick(MinecraftClient client) {
        LivingEntity previous = owner.get();
        // GameRenderer calls this hook every frame. When our feature is off and
        // owns no effect, return before touching ReplayMod/player state.
        if (!XPizzaXReplay.isNightVisionEnabled && previous == null) {
            ownedEffect = null;
            return;
        }

        boolean active = enabled(client);
        if (!active || previous != client.player) {
            if (previous != null && ownedEffect != null
                    && previous.getStatusEffect(StatusEffects.NIGHT_VISION) == ownedEffect)
                previous.removeStatusEffect(StatusEffects.NIGHT_VISION);
            owner.clear(); ownedEffect = null;
        }
        if (!active) return;
        StatusEffectInstance current = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current == null || current == ownedEffect && current.getDuration() < 400) {
            // Allocate once; refresh only near expiry, not on every rendered frame.
            var effect = new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100000, 0, false, false, false);
            client.player.addStatusEffect(effect);
            owner = new WeakReference<>(client.player);
            ownedEffect = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }
}
