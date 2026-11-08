package xpizzax.emoticons.mixin.client;
import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;
import xpizzax.emoticons.client.renderer.HeadPreviewScope;
import xpizzax.emoticons.ui.emoticons.EmoticonsScreen;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class VanillaEmoticonsRootMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void xpizzax$beginNpc(LivingEntity entity, float yaw, float delta,
            MatrixStack stack, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (!(entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity))
            VanillaEmoticonsBridge.beginEntity(entity, (LivingEntityRenderer<?, ?>)(Object)this, delta);
    }
    @Inject(method = "render", at = @At("RETURN"))
    private void xpizzax$endNpc(LivingEntity entity, float yaw, float delta,
            MatrixStack stack, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (!(entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity))
            VanillaEmoticonsBridge.end(entity, (LivingEntityRenderer<?, ?>)(Object)this);
    }
    @Inject(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void xpizzax$vanillaRoot(LivingEntity entity, float yaw, float delta,
            MatrixStack stack, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        // Easy NPC handles setAngles in BasePlayerModel and can skip the
        // PlayerEntityModel implementation containing our normal pose hook.
        // Apply once after the NPC has finished setting its own head angles.
        LivingEntityRenderer<?, ?> renderer = (LivingEntityRenderer<?, ?>) (Object) this;
        if (EmoticonsScreen.isHeadPreview() && renderer.getModel() instanceof PlayerEntityModel<?> preview) {
            HeadPreviewScope.mask(preview);
            return;
        }
        if ((!(entity instanceof AbstractClientPlayerEntity) || VanillaEmoticonsBridge.isFirstPersonBody(entity))
                && renderer.getModel() instanceof PlayerEntityModel<?> model) {
            float degrees = (float) (180.0 / java.lang.Math.PI);
            VanillaEmoticonsBridge.pose(entity, model,
                    model.head.yaw * degrees, model.head.pitch * degrees);
        }
        VanillaEmoticonsBridge.root(entity, stack);
    }
}
