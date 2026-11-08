package net.example.replaynv.mixin;
import net.example.replaynv.ReplayLighting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(GameRenderer.class)
public class LightmapTextureManagerMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void injectCinematicFullbright(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        ReplayLighting.tick(MinecraftClient.getInstance());
    }
    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void xpizzax$stableReplayLight(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity == client.player && ReplayLighting.enabled(client)) cir.setReturnValue(1F);
    }
}
