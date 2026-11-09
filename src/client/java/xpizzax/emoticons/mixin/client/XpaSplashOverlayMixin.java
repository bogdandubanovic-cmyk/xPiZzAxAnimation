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

/**
 * Adds updater status to the vanilla Minecraft startup/resource loading screen.
 */
@Mixin(SplashOverlay.class)
public abstract class XpaSplashOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void xpizzax$renderUpdateStatus(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String status = XpaUpdateStatus.getMessage();
        if (status == null || status.isBlank()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int x = context.getScaledWindowWidth() / 2;
        int y = context.getScaledWindowHeight() - 36;
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(status), x, y, 0xFFFFFF);
    }
}
