package xpizzax.emoticons.mixin.client;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xpizzax.emoticons.client.replay.RpmGenderReplay;
@Pseudo
@Mixin(targets="com.replaymod.replay.ReplayHandler",remap=false)
public abstract class ReplayGenderLifecycleMixin {
    @Inject(method={"<init>","restartedReplay"},at=@At("TAIL"),remap=false,require=0)
    private void rpmmodels$open(CallbackInfo ci){RpmGenderReplay.invalidateContext();}
    // Senders have terminated here; ReplayMod will commit our entry together
    // with its normal save instead of copying the whole archive a second time.
    @Inject(method="endReplay",at=@At(value="INVOKE",target="Lcom/replaymod/replaystudio/replay/ReplayFile;save()V"),remap=false)
    private void rpmmodels$save(CallbackInfo ci){RpmGenderReplay.prepareReplayClose();}
    @Inject(method="endReplay",at=@At("RETURN"),remap=false)
    private void rpmmodels$close(CallbackInfo ci){RpmGenderReplay.replayEnded();}
}
