package xpizzax.emoticons;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import xpizzax.emoticons.client.RpmAccessoryHelper;
import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;
import xpizzax.emoticons.client.replay.RpmGenderReplay;
import xpizzax.emoticons.ui.emoticons.EmoticonsScreen;
import xpizzax.emoticons.studio.CinematicStudio;
import xpizzax.emoticons.studio.CinematicStudioScreen;
import net.example.replaynv.ReplayState;
import net.example.replaynv.client.ReplayControls;

public final class EmoticonsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        xpizzax.emoticons.client.EyeAnimationController.register();

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return new Identifier("xpizzax_emoticons", "item_attachments");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        RpmAccessoryHelper.reload(manager);
                    }
                });

        KeyBinding menu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rpmmodels.eyes",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.rpmmodels.main"));

        Runnable openMenu = () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.setScreen(new EmoticonsScreen(mc.currentScreen));
        };

        ReplayControls.registerEmoticons(menu, openMenu);

        KeyBinding studio = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.xpizzaxanimation.studio", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7,
                "category.rpmmodels.main"));
        KeyBinding record = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.xpizzaxanimation.record", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8,
                "category.rpmmodels.main"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VanillaEmoticonsBridge.tickNpcCache(client);
            CinematicStudio.tick(client);
            while (studio.wasPressed()) {
                CinematicStudio.enterStudio(client);
                client.setScreen(new CinematicStudioScreen(client.currentScreen));
            }
            while (record.wasPressed()) {
                CinematicStudio.toggleRecording(client);
            }
            if (ReplayState.isInstalled()) {
                RpmGenderReplay.tick(client);
            }
            while (menu.wasPressed()) {
                openMenu.run();
            }
        });
    }
}
