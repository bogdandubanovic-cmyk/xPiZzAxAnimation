package xpizzax.emoticons.mixin.client;

import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;
import xpizzax.emoticons.animation.PoseRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Scope the missing attachment transform without changing the feature model itself. */
@Mixin(LivingEntityRenderer.class)
public abstract class VanillaEmoticonsFeatureMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "render", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/Entity;FFFFFF)V"))
    private void xpizzax$attachment(FeatureRenderer feature, MatrixStack stack,
            VertexConsumerProvider vertices, int light, Entity entity, float limbAngle,
            float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
        // The two menu cards show only the animated skin and eyes. Running every
        // installed armor/cape/item feature twice per frame made opening this
        // small settings screen needlessly expensive.
        if (entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity
                && xpizzax.emoticons.ui.emoticons.EmoticonsScreen.isMenuPreview()) return;
        boolean scoped = false;
        if (entity instanceof LivingEntity living && VanillaEmoticonsBridge.hasPose(living)) {
            if (PoseRenderer.isRpmGenderFeature(feature)) scoped = VanillaEmoticonsBridge.chest(living, stack);
            else if (feature instanceof HeadFeatureRenderer || feature instanceof HeldItemFeatureRenderer) scoped = VanillaEmoticonsBridge.upper(living, stack);
        }
        try { feature.render(stack, vertices, light, entity, limbAngle, limbDistance,
                tickDelta, age, headYaw, headPitch); }
        finally { if (scoped) stack.pop(); }
    }
}
