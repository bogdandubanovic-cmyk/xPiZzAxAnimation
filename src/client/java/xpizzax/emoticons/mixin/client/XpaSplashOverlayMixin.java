package xpizzax.emoticons.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xpizzax.emoticons.client.update.XpaUpdateStatus;

/** Adds xPiZzAxAnimation's updater progress to the vanilla startup screen. */
@Mixin(SplashOverlay.class)
public abstract class XpaSplashOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void xpizzax$renderUpdateStatus(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String status = XpaUpdateStatus.getMessage();
        if (status == null || status.isBlank()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int centerX = context.getScaledWindowWidth() / 2;
        int y = context.getScaledWindowHeight() - 43;
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(status), centerX, y, 0xFFFFFF);

        float progress = XpaUpdateStatus.getProgress();
        if (progress >= 0.0f) {
            int left = centerX - 110;
            int top = y + 15;
            context.fill(left, top, left + 220, top + 5, 0xFF333333);
            context.fill(left, top, left + Math.round(220 * progress), top + 5, 0xFF55DD88);
        }
    }
}
