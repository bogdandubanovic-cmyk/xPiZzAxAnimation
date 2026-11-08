package com.example.replaycarsbridge.mixin;

import com.example.replaycarsbridge.BridgeNet;
import io.netty.channel.ChannelHandlerContext;
import me.wattguy.cars.network.packets.Packet;
import me.wattguy.cars.network.socket.NettyPacketUdp;
import me.wattguy.cars.network.socket.UdpClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Точка входа входящих UDP-пакетов машин на клиенте
 * (me.wattguy.cars.network.socket.UdpClientHandler#channelRead0,
 * см. декомпиль — там сразу берётся уже готовый Packet<UdpClientHandler>
 * и диспатчится в handle(...)). Мы просто подслушиваем каждый такой
 * пакет ДО того, как Cars-мод его обработает, и зеркалим его копию
 * в ванильный протокол через BridgeNet — сам Cars-мод продолжает
 * работать штатно, мы ничего не отменяем и не трогаем.
 */
@Mixin(UdpClientHandler.class)
public class RecordVehicleUdpMixin {

    @Inject(method = "channelRead0", at = @At("HEAD"), remap = false)
    private void replaycarsbridge$onVehiclePacket(ChannelHandlerContext ctx, NettyPacketUdp packetUdp, CallbackInfo ci) {
        try {
            Packet<?> packet = packetUdp.packetUdp().getPacket();
            BridgeNet.recordIncomingCarsPacket(packet);
        } catch (Exception e) {
            BridgeNet.LOGGER.warn("Failed to read incoming Cars UDP packet for mirroring", e);
        }
    }
}
