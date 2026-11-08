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

/** Resolve the recorded profile at the rendering boundary, including exports. */
@Pseudo
@Mixin(targets = "com.wildfire.render.GenderLayer", remap = false)
public abstract class ReplayWildfireRenderMixin {
    @Inject(method = "getConfig", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xpizzax$recordedRenderConfig(LivingEntity entity, CallbackInfoReturnable<Object> ci) {
        if (!(entity instanceof PlayerEntity player)) return;
        Optional<?> state = RpmGenderReplay.playbackWildfire(player);
        if (state != null) {
            // Wildfire applies gender to its physics size gradually. During a
            // replay seek/export a cached physics size must never reveal a
            // profile recorded as Male, even for a single render frame.
            ci.setReturnValue(RpmGenderReplay.hideWildfireBreasts(state)
                    ? null : state.orElse(null));
        }
    }
}
