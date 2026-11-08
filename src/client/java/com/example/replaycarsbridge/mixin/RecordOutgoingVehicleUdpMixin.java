package com.example.replaycarsbridge.mixin;

import com.example.replaycarsbridge.BridgeNet;
import me.wattguy.cars.network.packets.Packet;
import me.wattguy.cars.network.socket.UdpClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the local driver's UDP updates, which the server may not echo back. */
@Mixin(UdpClient.class)
public class RecordOutgoingVehicleUdpMixin {
    @Inject(method = "sendPacket", at = @At("HEAD"), remap = false)
    private void replaycarsbridge$onSendPacket(Packet<?> packet, CallbackInfo ci) {
        BridgeNet.recordOutgoingCarsPacket(packet);
    }
}
