package net.example.replaynv.mixin;

import net.example.replaynv.ReplayEnvironment;
import net.example.replaynv.ReplayTimeAccess;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
public class ReplayTimeMixin implements ReplayTimeAccess {
    @Unique private boolean xpizzax$advancingTime;

    @Override
    public void xpizzax$setAdvancingTime(boolean advancing) {
        xpizzax$advancingTime = advancing;
    }

    @Inject(method = "getTimeOfDay()J", at = @At("RETURN"), cancellable = true)
    private void xpizzax$visibleTime(CallbackInfoReturnable<Long> cir) {
        if (xpizzax$advancingTime) return;
        var options = ReplayEnvironment.optionsForProperties(this);
        if (options.fixedTime()) cir.setReturnValue(options.visibleTime(cir.getReturnValueJ()));
        // Time packets, getTime() and animation clocks continue advancing normally.
    }
}
