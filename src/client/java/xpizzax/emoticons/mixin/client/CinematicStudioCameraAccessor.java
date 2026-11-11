package xpizzax.emoticons.mixin.client;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CinematicStudioCameraAccessor {
    @Invoker("setPos")
    void xpizzax$setPos(double x, double y, double z);

    @Invoker("setRotation")
    void xpizzax$setRotation(float yaw, float pitch);
}
