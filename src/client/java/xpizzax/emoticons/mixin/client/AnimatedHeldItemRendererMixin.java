package xpizzax.emoticons.mixin.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xpizzax.emoticons.client.renderer.AnimatedHandRenderScope;

/** Supply the wrist before a custom item renderer can bypass vanilla ItemRenderer. */
@Mixin(value = HeldItemRenderer.class, priority = 1100)
public abstract class AnimatedHeldItemRendererMixin {
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void xpizzax$handBeforeCustomRenderer(LivingEntity entity, ItemStack item,
            ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
            VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        AnimatedHandRenderScope.apply(item, mode, matrices);
    }
}
