package xpizzax.emoticons.mixin.client;

import net.example.replaynv.ReplayState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xpizzax.emoticons.studio.CinematicStudio;

/** Applies the studio camera only when explicitly enabled, never during ReplayMod playback. */
@Mixin(Camera.class)
public abstract class CinematicStudioCameraMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void xpizzax$applyStudioCamera(BlockView area, Entity focusedEntity,
            boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (ReplayState.isReplayOpen()) return;
        CinematicStudio.applyCamera((Camera)(Object)this);
    }
}
