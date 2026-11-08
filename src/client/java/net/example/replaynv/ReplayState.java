package net.example.replaynv;

import com.replaymod.replay.ReplayModReplay;
import net.fabricmc.loader.api.FabricLoader;

public final class ReplayState {
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("replaymod");
    private ReplayState() {}

    public static boolean isInstalled() {
        return INSTALLED;
    }

    public static boolean isReplayOpen() {
        if (!INSTALLED) return false;
        try {
            return ReplayApi.isOpen();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int replayTimeMs() {
        try {
            if (!isReplayOpen()) return -1;
            return ReplayApi.timeMs();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // Keep optional types out of the always-loaded class and its signatures.
    private static final class ReplayApi {
        static boolean isOpen() {
            return ReplayModReplay.instance != null && ReplayModReplay.instance.getReplayHandler() != null;
        }
        static int timeMs() {
            return ReplayModReplay.instance.getReplayHandler().getReplaySender().currentTimeStamp();
        }
    }
}
