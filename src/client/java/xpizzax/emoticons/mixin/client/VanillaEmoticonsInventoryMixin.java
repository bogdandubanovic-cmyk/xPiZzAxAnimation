package xpizzax.emoticons.mixin.client;

import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inventory temporarily replaces entity angles; previous world angles are unrelated. */
@Mixin(InventoryScreen.class)
public abstract class VanillaEmoticonsInventoryMixin {
    @Inject(method = {
        "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V",
        "drawEntity(Lnet/minecraft/client/gui/DrawContext;FFILorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V"
    }, at = @At("HEAD"))
    private static void xpizzax$previewStart(CallbackInfo ci) { VanillaEmoticonsBridge.beginPreview(); }
    @Inject(method = {
        "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V",
        "drawEntity(Lnet/minecraft/client/gui/DrawContext;FFILorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V"
    }, at = @At("RETURN"))
    private static void xpizzax$previewEnd(CallbackInfo ci) { VanillaEmoticonsBridge.endPreview(); }
}
