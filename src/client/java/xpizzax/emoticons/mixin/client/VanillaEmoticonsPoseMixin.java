package xpizzax.emoticons.mixin.client;
import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = PlayerEntityModel.class, priority = 500)
public abstract class VanillaEmoticonsPoseMixin {
    // PlayerEntityModel<T extends LivingEntity>: erased parameter is LivingEntity.
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void xpizzax$vanillaPose(LivingEntity entity, float limb, float amount, float age,
            float yaw, float pitch, CallbackInfo ci) {
        // Native hand renderers and NEA also run setAngles. The body pass is
        // applied after the complete call, after their final pose/reset hooks.
        if (!VanillaEmoticonsBridge.isFirstPersonBody(entity))
            VanillaEmoticonsBridge.pose(entity, (PlayerEntityModel<?>) (Object) this,yaw,pitch);
    }
}
