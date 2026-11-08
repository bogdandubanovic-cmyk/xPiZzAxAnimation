package xpizzax.emoticons.mixin.client;

import xpizzax.emoticons.client.replay.RpmGenderReplay;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

/** Return a profile owned by this replay, never mutate Wildfire's live cache. */
@Pseudo
@Mixin(targets = "com.wildfire.main.entitydata.EntityConfig", remap = false)
public abstract class ReplayWildfireConfigMixin {
    @Inject(method = "getEntity", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xpizzax$recordedWildfire(LivingEntity entity, CallbackInfoReturnable<Object> ci) {
        if (!(entity instanceof PlayerEntity player)) return;
        Optional<?> state = RpmGenderReplay.playbackWildfire(player);
        if (state != null) ci.setReturnValue(state.orElse(null));
    }
}
