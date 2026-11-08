package xpizzax.emoticons.mixin.client;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xpizzax.emoticons.client.replay.RpmGenderReplay;
@Mixin(GameRenderer.class)
public abstract class ReplayGenderFrameMixin {
    @Inject(method="render(FJZ)V",at=@At("HEAD"))
    private void rpmmodels$frame(float delta,long start,boolean tick,CallbackInfo ci){RpmGenderReplay.invalidateContext();}
}
