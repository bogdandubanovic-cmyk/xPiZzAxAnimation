package com.example.replaycarsbridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.example.replaynv.ReplayState;

public class ReplayCarsBridge implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Do not load BridgeNet or register callbacks referencing Cars when absent.
        if (!ReplayState.isInstalled() || !FabricLoader.getInstance().isModLoaded("cars")) {
            return;
        }
        BridgeNet.registerPlaybackReceiver();
        BridgeNet.registerRecordingHooks();
        BridgeNet.LOGGER.info("ReplayCars Bridge loaded: UDP-пакеты Cars будут зеркалиться в ванильный протокол во время записи ReplayMod и применяться обратно при воспроизведении.");
    }
}
