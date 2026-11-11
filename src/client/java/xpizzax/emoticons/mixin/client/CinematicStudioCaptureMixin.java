package xpizzax.emoticons.mixin.client;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xpizzax.emoticons.studio.CinematicStudio;

/**
 * Samples the completed vanilla player model after optional animation layers
 * (including Emotecraft) have modified its ModelParts.
 */
@Mixin(value = PlayerEntityModel.class, priority = 1)
public abstract class CinematicStudioCaptureMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void xpizzax$captureExternalEmotePose(LivingEntity entity, float limbAngle,
            float limbDistance, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        PlayerEntityModel<?> model = (PlayerEntityModel<?>)(Object)this;
        CinematicStudio.captureEmotecraftPose(entity, model);
        CinematicStudio.applyCapturedEmotecraftPose(entity, model);
    }
}
