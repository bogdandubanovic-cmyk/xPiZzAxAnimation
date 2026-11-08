package net.example.replaynv.client;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiOverlay;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.ReplayHandler;
import net.example.replaynv.ReplayEnvironment;
import net.example.replaynv.ReplayState;
import net.example.replaynv.XPizzaXReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

/** Replay shortcuts must work without world ticks, with the timeline hidden or paused. */
public final class ReplayControls {
    private static KeyBinding environmentKey, mediaKey, emoticonsKey;
    private static Runnable openMedia, openEmoticons;

    private ReplayControls() {}

    public static void registerEnvironment(KeyBinding environment) {
        environmentKey = environment;
    }
    public static void registerMedia(KeyBinding key, Runnable action) {
        mediaKey = key;
        openMedia = action;
    }
    public static void registerEmoticons(KeyBinding key, Runnable action) {
        emoticonsKey = key;
        openEmoticons = action;
    }

    public static boolean handleKey(long window, int key, int scanCode, int action, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ReplayState.isReplayOpen() || client.world == null || client.player == null
                || window != client.getWindow().getHandle() || !isReplayView(client.currentScreen)) return false;

        KeyBinding matched = matches(environmentKey, key, scanCode) ? environmentKey
                : matches(mediaKey, key, scanCode) ? mediaKey
                : matches(emoticonsKey, key, scanCode) ? emoticonsKey : null;
        if (matched == null || action == GLFW.GLFW_RELEASE) return false;
        // Consume repeats as well, but never toggle/open twice for one held key.
        if (action != GLFW.GLFW_PRESS) return true;
        matched.setPressed(false);
        while (matched.wasPressed()) {}
        ReplayEnvironment.tick(client);
        if (matched == environmentKey) {
            openEnvironment((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
        } else if (matched == mediaKey && openMedia != null) {
            openMedia.run();
        } else if (matched == emoticonsKey && openEmoticons != null) {
            openEmoticons.run();
        }
        return true;
    }

    private static boolean matches(KeyBinding binding, int key, int scanCode) {
        return binding != null && binding.matchesKey(key, scanCode);
    }
    private static boolean isReplayView(Screen screen) {
        return screen == null || ReplayAccess.isReplayView(screen);
    }

    /** Replay's input screen clears mouseVisible in removed(); restore it when returning. */
    public static final class ReturnTarget {
        private final Screen screen;
        private final Consumer<MinecraftClient> replayReturn;
        private final Object world;

        public ReturnTarget(Screen screen) {
            this.screen = screen;
            world = MinecraftClient.getInstance().world;
            replayReturn = ReplayState.isReplayOpen() ? ReplayAccess.returnAction(screen) : null;
        }
        public void restore(MinecraftClient client) {
            if (replayReturn == null) {
                client.setScreen(!ReplayState.isReplayOpen() && client.world == world ? screen : null);
                return;
            }
            replayReturn.accept(client);
        }
    }

    /** Only reached after ReplayState confirms an active replay. */
    private static final class ReplayAccess {
        static boolean isReplayView(Screen screen) {
            return OverlayAccess.isInputFor(screen, ReplayModReplay.instance.getReplayHandler().getOverlay());
        }
        static Consumer<MinecraftClient> returnAction(Screen screen) {
            ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
            if (handler == null) return null;
            boolean mouseVisible = handler.getOverlay().isMouseVisible();
            return client -> {
                if (!ReplayState.isReplayOpen() || ReplayModReplay.instance.getReplayHandler() != handler) {
                    client.setScreen(null);
                    return;
                }
                if (OverlayAccess.isInputFor(screen, handler.getOverlay())) {
                    handler.getOverlay().setMouseVisible(mouseVisible);
                }
                client.setScreen(screen);
            };
        }
        private static final class OverlayAccess extends AbstractGuiOverlay<OverlayAccess> {
            @Override protected OverlayAccess getThis() { return this; }
            static boolean isInputFor(Screen screen, AbstractGuiOverlay<?> overlay) {
                return screen instanceof AbstractGuiOverlay<?>.UserInputGuiScreen input && input.getOverlay() == overlay;
            }
        }
    }

    public static void openEnvironment(boolean quickDay) {
        if (!ReplayState.isReplayOpen()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ReplayEnvironment.tick(client);
        if (quickDay) {
            var options = ReplayEnvironment.options();
            ReplayEnvironment.setOptions(XPizzaXReplay.isAlwaysDayEnabled
                    ? options.withRecordedTime() : options.withTime(6000));
            ReplayEnvironment.savePending();
        } else {
            client.setScreen(new ReplayEnvironmentScreen(client.currentScreen));
        }
    }
}
