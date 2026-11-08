package com.example.replaycarsbridge.mixin;

import com.example.replaycarsbridge.ReplayTransportStandVisibility;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.ArmorStandEntityRenderer;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStandEntityRenderer.class)
public abstract class ReplayTransportStandRendererMixin {
    @Inject(method="getRenderLayer(Lnet/minecraft/entity/decoration/ArmorStandEntity;ZZZ)Lnet/minecraft/client/render/RenderLayer;",
            at=@At("HEAD"),cancellable=true)
    private void xpizzax$hideTransportStandBody(ArmorStandEntity stand,boolean showBody,boolean translucent,
                                           boolean outline,CallbackInfoReturnable<RenderLayer> cir) {
        // Skip just the wooden model. Feature layers still render vehicle/wheel/seat items.
        if(ReplayTransportStandVisibility.hideBody(stand))cir.setReturnValue(null);
    }
}
