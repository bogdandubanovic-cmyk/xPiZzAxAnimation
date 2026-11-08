package xpizzax.emoticons.mixin.client;

import xpizzax.emoticons.animation.PoseRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * xPiZzAxAnimation bridge for PlayerAnimator/bendy-lib + Dynamix.
 * Dynamix 1.2.0 calls the context model's copyBipedStateTo(armorModel)
 * immediately before rendering the armor. Apply our BOBJ bend at TAIL so
 * it lands on the exact model Dynamix will render, after its state copy.
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelBendMixin<T extends LivingEntity>
{
    @Inject(method = "copyBipedStateTo", at = @At("TAIL"))
    private void xpizzax$applyBobjBendAfterCopy(BipedEntityModel<T> target, CallbackInfo ci)
    {
        PoseRenderer.xpizzaxApplyActiveBendToCopiedArmor(target);
    }
}
