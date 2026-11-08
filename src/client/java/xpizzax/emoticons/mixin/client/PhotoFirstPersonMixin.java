package xpizzax.emoticons.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xpizzax.emoticons.client.PhotoItemCompatibility;

/** Keep the photo hand when FirstPerson would switch to its hidden body arms. */
@Pseudo
@Mixin(targets = "dev.tr7zw.firstperson.LogicHandler", remap = false)
public abstract class PhotoFirstPersonMixin {
    @Inject(method = "showVanillaHands()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void rpm$showPhotoHand(CallbackInfoReturnable<Boolean> ci) {
        if (PhotoItemCompatibility.hideFirstPersonBody(MinecraftClient.getInstance().player)) ci.setReturnValue(true);
    }

    @Inject(method = "dynamicHandsEnabled", at = @At("HEAD"), cancellable = true, remap = false)
    private void rpm$keepPhotoVisibleWhenLookingDown(CallbackInfoReturnable<Boolean> ci) {
        if (PhotoItemCompatibility.hideFirstPersonBody(MinecraftClient.getInstance().player)) ci.setReturnValue(false);
    }
}
