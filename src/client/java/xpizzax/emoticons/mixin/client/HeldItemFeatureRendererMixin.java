package xpizzax.emoticons.mixin.client;

import xpizzax.emoticons.client.RpmAccessoryHelper;
import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;
import xpizzax.emoticons.client.renderer.AnimatedHandRenderScope;
import xpizzax.emoticons.client.renderer.BackAccessoryRenderScope;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HeldItemFeatureRenderer.class, priority = 1100)
public abstract class HeldItemFeatureRendererMixin
{
    @Shadow @Final private HeldItemRenderer heldItemRenderer;
    @Shadow protected abstract void renderItem(LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, Arm arm, MatrixStack matrices,
            VertexConsumerProvider vertices, int light);

    @Redirect(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/entity/feature/HeldItemFeatureRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void xpizzax$handDrawScope(HeldItemFeatureRenderer feature, LivingEntity entity,
            ItemStack item, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
            VertexConsumerProvider vertices, int light) {
        // Scope the caller, so it also closes when RPM cancels renderItem at HEAD.
        // Preserve the physical arm independently of the pack's item display.
        ModelWithArms vanilla = feature.getContextModel() instanceof ModelWithArms model ? model : null;
        try (AnimatedHandRenderScope scope = AnimatedHandRenderScope.begin(entity, item, arm, matrices, vanilla)) {
            this.renderItem(entity, item, mode, arm, matrices, vertices, light);
        }
    }

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void xpizzax$animatedLeftGrip(LivingEntity entity, ItemStack stack,
            ModelTransformationMode mode, Arm arm, MatrixStack matrices,
            VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (arm != Arm.LEFT || mode != ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                || !VanillaEmoticonsBridge.hasPose(entity)
                || !RpmAccessoryHelper.isUmbrellaItem(stack)) return;

        // RPMClient's legacy HEAD hook can render with fixed rotations and
        // cancel vanilla before setArmAngle (and our forearm hook) is reached.
        // Only these handheld pack models bypass that path while our pose is active.
        matrices.push();
        try {
            if (!VanillaEmoticonsBridge.bindHand(entity, matrices, arm)) return;
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180F));
            matrices.translate(-1F / 16F, 0.125F, -0.625F);
            this.heldItemRenderer.renderItem(entity, stack, mode,
                    true, matrices, vertices, light);
        } finally {
            matrices.pop();
        }
        ci.cancel();
    }

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void xpizzax$bodyAccessoryNotInHand(LivingEntity entity, ItemStack stack,
                                                 ModelTransformationMode mode, Arm arm,
                                                 MatrixStack matrices, VertexConsumerProvider vertices,
                                                 int light, CallbackInfo ci)
    {
        // RPM and other renderers may pass a copy of the equipped ItemStack.
        // Identify offhand by its physical arm, never by object identity.
        if (arm == entity.getMainArm().getOpposite()
                && BackAccessoryRenderScope.isBodyAccessory(entity, stack))
        {
            ci.cancel();
        }
    }
    // VanillaEmoticonsFeatureMixin already applies the torso bend once to this
    // feature. Only add the forearm bend after vanilla has positioned the arm.
    @Inject(method = "renderItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/entity/model/ModelWithArms;setArmAngle(Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V", shift = At.Shift.AFTER))
    private void xpizzax$followForearm(LivingEntity entity, ItemStack item,
            ModelTransformationMode mode, Arm arm, MatrixStack matrices,
            VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        VanillaEmoticonsBridge.hand(entity, matrices, arm);
        AnimatedHandRenderScope.markNativeBinding(item, arm);
    }
}
