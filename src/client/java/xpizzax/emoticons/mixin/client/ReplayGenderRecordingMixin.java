package xpizzax.emoticons.mixin.client;

import xpizzax.emoticons.client.replay.RpmGenderReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.netty.channel.ChannelHandlerContext;

@Pseudo
@Mixin(targets = "com.replaymod.recording.packet.PacketListener", remap = false)
public abstract class ReplayGenderRecordingMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 0)
    private void xpizzax$startGenderRecording(CallbackInfo ci) { RpmGenderReplay.begin(this); }

    @Inject(method = "channelInactive", at = @At("HEAD"), remap = false, require = 0)
    private void xpizzax$saveGenderRecording(ChannelHandlerContext context, CallbackInfo ci) {
        RpmGenderReplay.finish(this);
    }
}
