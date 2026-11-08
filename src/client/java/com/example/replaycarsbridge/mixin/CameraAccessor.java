package com.example.replaycarsbridge.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPos")
    void replaycarsbridge$setPos(double x, double y, double z);

    @Invoker("setRotation")
    void replaycarsbridge$setRotation(float yaw, float pitch);
}
