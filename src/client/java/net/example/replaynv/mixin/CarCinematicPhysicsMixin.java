package net.example.replaynv.mixin;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

@Mixin(EntityRenderDispatcher.class)
public class CarCinematicPhysicsMixin {

    private static final class SpeedState {
        double last;
        SpeedState(double initial) { last = initial; }
    }

    // Entity instances are only needed while their render objects are alive.
    // Weak keys prevent old replay/world vehicles from accumulating forever;
    // the mutable value avoids boxing/allocating a Double on every rendered frame.
    private static final Map<Entity, SpeedState> LAST_SPEEDS = new WeakHashMap<>();
    private static final ThreadLocal<Quaternionf> ROTATION =
            ThreadLocal.withInitial(Quaternionf::new);

    // Entity class names never change. Do the string matching once per class,
    // instead of allocating/lowercasing a name for every rendered entity/frame.
    private static final ClassValue<Boolean> TARGET_VEHICLE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            String name = type.getName().toLowerCase(Locale.ROOT);
            return name.contains("car") || name.contains("vehicle")
                    || name.contains("updaterx") || name.contains("rpmclient")
                    || name.contains("drive");
        }
    };

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    private <E extends Entity> void injectCarPhysics(E entity, double x, double y, double z,
            float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, CallbackInfo ci) {
        var world = entity.getWorld();
        if (world == null || !world.isClient() || !TARGET_VEHICLE.get(entity.getClass())) {
            return;
        }

        double speedX = entity.getX() - entity.prevX;
        double speedZ = entity.getZ() - entity.prevZ;
        double currentSpeed = Math.sqrt(speedX * speedX + speedZ * speedZ);
        SpeedState state = LAST_SPEEDS.get(entity);
        double lastSpeed = currentSpeed;
        if (state == null) {
            LAST_SPEEDS.put(entity, new SpeedState(currentSpeed));
        } else {
            lastSpeed = state.last;
            state.last = currentSpeed;
        }
        double acceleration = currentSpeed - lastSpeed;
        float pitchLeanX = 0.0f;

        if (Math.abs(acceleration) > 0.001) {
            if (acceleration > 0) {
                pitchLeanX = (float) Math.toRadians(-Math.min(5.5, acceleration * 75.0));
            } else {
                pitchLeanX = (float) Math.toRadians(Math.min(7.5, Math.abs(acceleration) * 105.0));
            }
            matrices.multiply(ROTATION.get().rotationX(pitchLeanX));
        }

        if (currentSpeed > 0.04) {
            float movementYaw = (float) Math.toDegrees(Math.atan2(speedX, speedZ));
            float driftAngle = movementYaw - entity.getYaw();

            while (driftAngle < -180) driftAngle += 360;
            while (driftAngle > 180) driftAngle -= 360;

            if (Math.abs(driftAngle) > 7 && Math.abs(driftAngle) < 90) {
                float rollLeanZ = (float) Math.toRadians(
                        Math.signum(driftAngle) * Math.min(6.5, currentSpeed * 11.0));
                matrices.multiply(ROTATION.get().rotationZ(rollLeanZ));
            }
        }
    }
}
