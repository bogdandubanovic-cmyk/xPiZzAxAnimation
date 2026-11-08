package net.example.replaynv;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.example.replaynv.client.ReplayControls;
import org.lwjgl.glfw.GLFW;

public class XPizzaXReplay implements ClientModInitializer {
    public static boolean isNightVisionEnabled = false;
    public static boolean isAlwaysDayEnabled = false;

    private static KeyBinding dayToggleKey;

    @Override
    public void onInitializeClient() {
        if (!ReplayState.isInstalled()) return;
        System.out.println("[xPiZzAxAnimation] cinematic core loaded");
        ReplayEnvironment.register();

        dayToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rpmmodels.environment", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
                "category.rpmmodels.main"));
        ReplayControls.registerEnvironment(dayToggleKey);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ReplayEnvironment.tick(client);

            // Maintain session state and support programmatically queued key bindings.
            // Physical replay keys are also handled immediately by ReplayKeyboardMixin.
            if (!ReplayState.isReplayOpen()) {
                while (dayToggleKey.wasPressed()) {}
                if (isNightVisionEnabled || isAlwaysDayEnabled) {
                    isNightVisionEnabled = false;
                    isAlwaysDayEnabled = false;
                    ReplayLighting.tick(client);
                    if (client.gameRenderer != null) client.gameRenderer.getLightmapTextureManager().tick();
                }
                return;
            }

            if (client.player == null || client.world == null) return;

            while (dayToggleKey.wasPressed()) {
                ReplayControls.openEnvironment(net.minecraft.client.gui.screen.Screen.hasShiftDown());
            }
        });
    }

    public static void toggleNightVision() {
        if (!ReplayState.isReplayOpen()) return;
        var client = net.minecraft.client.MinecraftClient.getInstance();
        isNightVisionEnabled = !isNightVisionEnabled;
        ReplayLighting.tick(client);
        if (client.gameRenderer != null) client.gameRenderer.getLightmapTextureManager().tick();
        if (client.player != null) client.player.sendMessage(Text.literal("Ночное зрение в реплее: "
                + (isNightVisionEnabled ? "§aВКЛЮЧЕНО" : "§cВЫКЛЮЧЕНО")), true);
    }
}
