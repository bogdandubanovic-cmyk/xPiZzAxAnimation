package com.example.replaycarsbridge.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Достаёт приватное поле Netty-канала у ванильного Connection (class_2535).
 * ReplayMod вешает на этот же канал свои хендлеры "replay_recorder_raw" /
 * "replay_recorder_decoded" (см. com.replaymod.recording.mixin.MixinClientConnection,
 * поле field_11651 в интермедиари-именах). В Yarn 1.20.4 это поле называется
 * "channel" — если у тебя в IDE после ремапа Loom оно называется иначе,
 * поправь имя в @Accessor или подставь через SRG/intermediary напрямую.
 */
@Mixin(ClientConnection.class)
public interface ConnectionChannelAccessor {
    @Accessor("channel")
    Channel replaycarsbridge$getChannel();
}
