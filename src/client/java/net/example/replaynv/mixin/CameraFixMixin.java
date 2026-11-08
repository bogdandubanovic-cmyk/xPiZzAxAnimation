package net.example.replaynv.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class CameraFixMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void forceRecordCamera(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Если мы в реплее — ничего не делаем
        if (client.world == null) return;

        // Проверяем, сидит ли игрок в машине (проверяем наличие транспорта)
        if (client.player != null && client.player.getVehicle() != null) {
            // Если игрок в машине, берем углы его обзора и принудительно обновляем их
            // Это заставит Replay Mod думать, что игрок крутит головой, и запишет движение камеры!
            client.player.prevYaw = client.player.getYaw();
            client.player.prevPitch = client.player.getPitch();
        }
    }
}
