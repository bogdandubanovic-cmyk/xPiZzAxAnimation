package net.example.replaynv.mixin;

import net.example.replaynv.ReplayTimeAccess;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldTimeMixin {
    @Inject(method = "tickTime()V", at = @At("HEAD"))
    private void xpizzax$beforeClockTick(CallbackInfo ci) {
        ((ReplayTimeAccess) ((ClientWorld) (Object) this).getLevelProperties())
                .xpizzax$setAdvancingTime(true);
    }
    @Inject(method = "tickTime()V", at = @At("RETURN"))
    private void xpizzax$afterClockTick(CallbackInfo ci) {
        ((ReplayTimeAccess) ((ClientWorld) (Object) this).getLevelProperties())
                .xpizzax$setAdvancingTime(false);
    }
}
