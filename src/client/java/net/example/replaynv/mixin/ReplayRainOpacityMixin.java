package net.example.replaynv.mixin;

import net.example.replaynv.ReplayEnvironment;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Change only the four rain vertices; sky, fog, snow and world weather stay native. */
@Mixin(WorldRenderer.class)
public abstract class ReplayRainOpacityMixin {
    @Unique private float xpizzax$rainOpacity = 1F;

    @Inject(method = "renderWeather(Lnet/minecraft/client/render/LightmapTextureManager;FDDD)V",
            at = @At("HEAD"))
    private void xpizzax$beginRain(CallbackInfo ci) {
        xpizzax$rainOpacity = ReplayEnvironment.rainOpacity();
    }

    @ModifyArg(method = "renderWeather(Lnet/minecraft/client/render/LightmapTextureManager;FDDD)V",
            slice = @Slice(
                    from = @At(value = "FIELD", target = "Lnet/minecraft/client/render/WorldRenderer;RAIN:Lnet/minecraft/util/Identifier;"),
                    to = @At(value = "FIELD", target = "Lnet/minecraft/client/render/WorldRenderer;SNOW:Lnet/minecraft/util/Identifier;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;"),
            index = 3, require = 4)
    private float xpizzax$rainAlpha(float alpha) {
        return alpha * xpizzax$rainOpacity;
    }
}
