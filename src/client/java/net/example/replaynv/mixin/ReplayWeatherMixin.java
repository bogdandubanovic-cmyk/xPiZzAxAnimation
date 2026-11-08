package net.example.replaynv.mixin;

import net.example.replaynv.ReplayEnvironment;
import net.example.replaynv.ReplayEnvironmentOptions;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class ReplayWeatherMixin {
    @Inject(method = "getRainGradient(F)F", at = @At("HEAD"), cancellable = true)
    private void xpizzax$rain(float delta, CallbackInfoReturnable<Float> cir) {
        var options = ReplayEnvironment.optionsFor((World) (Object) this);
        if (options.weather() != ReplayEnvironmentOptions.Weather.RECORDED)
            cir.setReturnValue(options.rainGradient(0F));
    }
    @Inject(method = "getThunderGradient(F)F", at = @At("HEAD"), cancellable = true)
    private void xpizzax$thunder(float delta, CallbackInfoReturnable<Float> cir) {
        var options = ReplayEnvironment.optionsFor((World) (Object) this);
        if (options.weather() != ReplayEnvironmentOptions.Weather.RECORDED)
            cir.setReturnValue(options.thunderGradient(0F));
    }
    @Inject(method = "isRaining()Z", at = @At("HEAD"), cancellable = true)
    private void xpizzax$raining(CallbackInfoReturnable<Boolean> cir) {
        var options = ReplayEnvironment.optionsFor((World) (Object) this);
        if (options.weather() != ReplayEnvironmentOptions.Weather.RECORDED)
            cir.setReturnValue(options.raining());
    }
    @Inject(method = "isThundering()Z", at = @At("HEAD"), cancellable = true)
    private void xpizzax$thundering(CallbackInfoReturnable<Boolean> cir) {
        var options = ReplayEnvironment.optionsFor((World) (Object) this);
        if (options.weather() != ReplayEnvironmentOptions.Weather.RECORDED)
            cir.setReturnValue(options.thundering());
    }
}
