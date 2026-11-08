package net.example.replaynv.mixin;

import net.example.replaynv.client.ReplayControls;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run before ReplayMod's GUI/key filters, even when no replay ticks are advancing. */
@Mixin(value = Keyboard.class, priority = 2000)
public abstract class ReplayKeyboardMixin {
    @Inject(method = "onKey(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void xpizzax$replayKey(long window, int key, int scanCode, int action, int modifiers,
                                  CallbackInfo ci) {
        if (ReplayControls.handleKey(window, key, scanCode, action, modifiers)) ci.cancel();
    }
}
