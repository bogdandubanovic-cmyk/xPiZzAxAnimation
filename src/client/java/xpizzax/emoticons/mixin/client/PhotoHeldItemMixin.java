package xpizzax.emoticons.mixin.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xpizzax.emoticons.client.PhotoItemCompatibility;
import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;

/** NEA draws map pixels directly, never reaching HeldItemRenderer's grip hook. */
@Pseudo
@Mixin(targets = "dev.tr7zw.notenoughanimations.logic.HeldItemHandler", remap = false)
public abstract class PhotoHeldItemMixin {
    @Inject(method = "onRenderItem", remap = false,
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/client/render/entity/model/ModelWithArms;setArmAngle(Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V",
                    shift = At.Shift.AFTER, remap = true))
    private void rpm$photoFollowsForearm(LivingEntity entity, EntityModel<?> model,
            ItemStack item, Arm arm, MatrixStack matrices,
            VertexConsumerProvider vertices, int light, CallbackInfo original,
            CallbackInfo ci) {
        // Keep NEA's map placement/handedness, adding only the missing elbow.
        if (PhotoItemCompatibility.isPhoto(item)) VanillaEmoticonsBridge.hand(entity, matrices, arm);
    }
}
