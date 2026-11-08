package com.example.replaycarsbridge;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.replaymod.recording.packet.PacketListener;
import com.example.replaycarsbridge.mixin.CameraAccessor;
import com.example.replaycarsbridge.mixin.ConnectionChannelAccessor;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import me.wattguy.cars.infos.Car;
import me.wattguy.cars.imixins.ICarLivingEntity;
import me.wattguy.cars.network.packets.udp.bothbound.*;
import me.wattguy.cars.utils.CameraHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Quaterniond;

/**
 * Прокидывает пакеты синхронизации машин (Cars mod, отдельный UDP-сокет)
 * через ванильный протокол, чтобы:
 *  1) ReplayMod во время ЗАПИСИ увидел их и сохранил в .mcpr
 *     (мод-рекордер слушает netty-пайплайн обычного Connection, а
 *     сырой UDP Cars-мода этот пайплайн вообще не трогает);
 *  2) при ВОСПРОИЗВЕДЕНИИ реплея наш приёмник получил их обратно
 *     (ReplayMod прогоняет записанные пакеты через тот же клиентский
 *     пайплайн, включая обработку custom payload) и применил
 *     положение/поворот/угол колёс к уже существующей (воссозданной
 *     из VehicleInfoPacket) машине.
 *
 * ВАЖНО: этот класс работает только когда реально идёт запись ReplayMod'ом
 * (проверяем наличие хендлера "replay_recorder_decoded" в пайплайне) —
 * в остальное время просто ничего не делает и не мешает обычной игре.
 */
public final class BridgeNet {

    public static final Logger LOGGER = LogManager.getLogger("ReplayCarsBridge");

    public static final Identifier CHANNEL = new Identifier("replaycarsbridge", "car_udp");

    /** Метки типов пакетов — используем при (де)сериализации. Порядок не важен,
     *  важно чтобы id совпадали между записью и чтением (они всегда совпадают,
     *  т.к. это один и тот же класс на клиенте). */
    private static final int T_MOVE = 0;
    private static final int T_ROTATE = 1;
    private static final int T_BOOST = 2;
    private static final int T_CHARGE = 3;
    private static final int T_DRIFT = 4;
    private static final int T_HORN = 5;
    private static final int T_SPEED = 6;
    private static final int T_CAMERA = 7;
    private static final int T_CAMERA_RENDERED = 8;

    /** IDs currently driven by this client. Server echoes for them are delayed and cause jitter. */
    private static final Map<Integer, Long> LOCALLY_DRIVEN = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Object> LATEST_LOCAL_PACKETS = new ConcurrentHashMap<>();
    private static volatile CameraFrame playbackCamera;
    private static int heartbeatTicks;

    private BridgeNet() {}

    // =========================== ЗАПИСЬ ===========================

    /**
     * Вызывается из RecordVehicleUdpMixin для каждого входящего UDP-пакета
     * Cars-мода. Сериализует его в компактный буфер и (если ReplayMod сейчас
     * реально пишет реплей) впрыскивает как обычный vanilla custom-payload
     * пакет прямо в клиентский netty-пайплайн — локально, без похода в сеть.
     */
    public static void recordOutgoingCarsPacket(Object carsPacket) {
        int main = getMainId(carsPacket);
        if (main >= 0) LOCALLY_DRIVEN.put(main, System.currentTimeMillis());
        if (main >= 0) LATEST_LOCAL_PACKETS.put(carsPacket.getClass(), carsPacket);
        recordCarsPacket(carsPacket);
    }

    public static void recordIncomingCarsPacket(Object carsPacket) {
        int main = getMainId(carsPacket);
        Long lastLocalUpdate = LOCALLY_DRIVEN.get(main);
        if (lastLocalUpdate != null) {
            if (System.currentTimeMillis() - lastLocalUpdate < 10_000L) return;
            LOCALLY_DRIVEN.remove(main, lastLocalUpdate);
        }
        recordCarsPacket(carsPacket);
    }

    private static int getMainId(Object packet) {
        if (packet instanceof VehicleMovePacket p) return p.getMain();
        if (packet instanceof VehicleRotatePacket p) return p.getMain();
        if (packet instanceof VehicleBoostPacket p) return p.getMain();
        if (packet instanceof VehicleChargePacket p) return p.getMain();
        if (packet instanceof VehicleDriftPacket p) return p.getMain();
        if (packet instanceof VehicleHornPacket p) return p.getMain();
        if (packet instanceof VehicleSpeedPacket p) return p.getMain();
        return -1;
    }

    private static void recordCarsPacket(Object carsPacket) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        boolean known = true;

        if (carsPacket instanceof VehicleMovePacket p) {
            out.writeInt(T_MOVE);
            out.writeInt(p.getMain());
            out.writeFloat(p.getLocation().getX());
            out.writeFloat(p.getLocation().getY());
            out.writeFloat(p.getLocation().getZ());
            out.writeFloat(p.getLocation().getYaw());
            out.writeFloat(p.getLocation().getPitch());
            out.writeInt(p.getAcceleration().getId());
        } else if (carsPacket instanceof VehicleRotatePacket p) {
            out.writeInt(T_ROTATE);
            out.writeInt(p.getMain());
            out.writeFloat(p.getWheelAngle());
            out.writeFloat(p.getFrontWheelRotation());
            out.writeFloat(p.getRearWheelRotation());
        } else if (carsPacket instanceof VehicleBoostPacket p) {
            out.writeInt(T_BOOST);
            out.writeInt(p.getMain());
            out.writeBoolean(p.isBoosting());
        } else if (carsPacket instanceof VehicleChargePacket p) {
            out.writeInt(T_CHARGE);
            out.writeInt(p.getMain());
            out.writeBoolean(p.isCharging());
        } else if (carsPacket instanceof VehicleDriftPacket p) {
            out.writeInt(T_DRIFT);
            out.writeInt(p.getMain());
            out.writeBoolean(p.isDrifting());
            out.writeFloat(p.getAdditionalYaw());
        } else if (carsPacket instanceof VehicleHornPacket p) {
            out.writeInt(T_HORN);
            out.writeInt(p.getMain());
            out.writeBoolean(p.isHorn());
        } else if (carsPacket instanceof VehicleSpeedPacket p) {
            out.writeInt(T_SPEED);
            out.writeInt(p.getMain());
            out.writeFloat(p.getSpeed());
        } else {
            known = false;
        }

        if (!known) return;

        injectIntoOwnConnectionIfRecording(out);
    }

    public static void registerRecordingHooks() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            playbackCamera = null;
            LOCALLY_DRIVEN.clear();
            LATEST_LOCAL_PACKETS.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++heartbeatTicks >= 20) {
                heartbeatTicks = 0;
                // A periodic duplicate of the newest local state prevents the
                // final second from being lost when the connection closes.
                LATEST_LOCAL_PACKETS.values().forEach(BridgeNet::recordCarsPacket);
            }
        });

        WorldRenderEvents.AFTER_SETUP.register(context -> {
            Camera camera = context.camera();
            CameraFrame frame = playbackCamera;
            if (frame != null && camera.getFocusedEntity() != null
                    && camera.getFocusedEntity().getId() == frame.focusedEntityId()) {
                ((CameraAccessor) (Object) camera).replaycarsbridge$setPos(frame.x(), frame.y(), frame.z());
                ((CameraAccessor) (Object) camera).replaycarsbridge$setRotation(frame.yaw(), frame.pitch());
                CameraHandler.yawOffset = frame.yawOffset();
                CameraHandler.pitchOffset = frame.pitchOffset();
                CameraHandler.currentRotation = new Quaterniond(frame.qx(), frame.qy(), frame.qz(), frame.qw());
                CameraHandler.prevRotation = new Quaterniond(frame.pqx(), frame.pqy(), frame.pqz(), frame.pqw());
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || camera.getFocusedEntity() != client.player) return;
            Entity vehicle = client.player.getVehicle();
            if (!(vehicle instanceof ICarLivingEntity living) || !living.hasCars$car()) return;
            recordRenderedCamera(camera);
        });
    }

    private static void recordRenderedCamera(Camera camera) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        Quaterniond current = CameraHandler.currentRotation;
        Quaterniond previous = CameraHandler.prevRotation;
        out.writeInt(T_CAMERA_RENDERED);
        out.writeInt(camera.getFocusedEntity().getId());
        out.writeDouble(camera.getPos().x);
        out.writeDouble(camera.getPos().y);
        out.writeDouble(camera.getPos().z);
        out.writeFloat(camera.getYaw());
        out.writeFloat(camera.getPitch());
        out.writeFloat(CameraHandler.yawOffset);
        out.writeFloat(CameraHandler.pitchOffset);
        out.writeDouble(current.x());
        out.writeDouble(current.y());
        out.writeDouble(current.z());
        out.writeDouble(current.w());
        out.writeDouble(previous.x());
        out.writeDouble(previous.y());
        out.writeDouble(previous.z());
        out.writeDouble(previous.w());
        injectIntoOwnConnectionIfRecording(out);
    }

    private static void injectIntoOwnConnectionIfRecording(ByteArrayDataOutput out) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() == null) return;

            Channel channel = ((ConnectionChannelAccessor) (Object) mc.getNetworkHandler().getConnection())
                    .replaycarsbridge$getChannel();
            if (channel == null) return;

            ChannelPipeline pipeline = channel.pipeline();
            // ReplayMod вешает этот хендлер, только когда реально идёт запись.
            if (!(pipeline.get("replay_recorder_raw") instanceof PacketListener recorder)) {
                return; // не пишем реплей прямо сейчас — ничего впрыскивать не нужно
            }

            byte[] bytes = out.toByteArray();
            PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
            // 1.20.4: конструктор CustomPayloadS2CPacket(Identifier, PacketByteBuf) напрямую,
            // без обёртки CustomPayload (та появилась только в более поздних версиях протокола).
            Packet<ClientCommonPacketListener> fakePacket = ServerPlayNetworking.createS2CPacket(CHANNEL, buf);

            // UDP-пакет возник вне ванильного канала, поэтому передаём его
            // непосредственно активному рекордеру ReplayMod. Вставка после
            // decoder здесь не подходит: raw-рекордер расположен перед ним.
            recorder.save(fakePacket);
        } catch (Throwable t) {
            LOGGER.warn("Failed to mirror Cars UDP packet into vanilla protocol", t);
        }
    }

    // ===================== ВОСПРОИЗВЕДЕНИЕ (И ОБЫЧНАЯ ИГРА) =====================

    /** Вызывается один раз из ReplayCarsBridge#onInitializeClient(). */
    public static void registerPlaybackReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, sender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            client.execute(() -> applyRecordedPacket(data));
        });
    }

    private static void applyRecordedPacket(byte[] data) {
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            int type = in.readInt();
            int main = -1;
            switch (type) {
                case T_MOVE -> {
                    main = in.readInt();
                    float x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
                    float yaw = in.readFloat(), pitch = in.readFloat();
                    int accId = in.readInt();
                    withCar(main, (car, mainEntity, living) -> {
                        if (living != null) living.setCars$mounting(true);
                        car.getRemotes().setPosition(new Vec3d(x, y, z), 3);
                        car.getRemotes().setRotation(new Vec2f(pitch, yaw), 3);
                        car.setAcceleration(me.wattguy.cars.utils.IAccelerationDirection.fromId(accId));
                    });
                }
                case T_ROTATE -> {
                    main = in.readInt();
                    float wheelAngle = in.readFloat(), front = in.readFloat(), rear = in.readFloat();
                    withCar(main, (car, mainEntity, living) -> {
                        if (living != null) living.setCars$mounting(true);
                        car.getRemotes().setWheelAngle(wheelAngle, 3);
                        car.getRemotes().setFrontWheelRotation(front, 3);
                        car.getRemotes().setRearWheelRotation(rear, 3);
                    });
                }
                case T_BOOST -> {
                    main = in.readInt();
                    boolean boosting = in.readBoolean();
                    withCar(main, (car, mainEntity, living) -> car.setBoosting(boosting));
                }
                case T_CHARGE -> {
                    main = in.readInt();
                    boolean charging = in.readBoolean();
                    withCar(main, (car, mainEntity, living) -> car.setCharging(charging));
                }
                case T_DRIFT -> {
                    main = in.readInt();
                    boolean drifting = in.readBoolean();
                    float additionalYaw = in.readFloat();
                    withCar(main, (car, mainEntity, living) -> {
                        car.setDrifting(drifting);
                        car.getRemotes().setAdditionalYaw(additionalYaw, 3);
                    });
                }
                case T_HORN -> {
                    main = in.readInt();
                    boolean horn = in.readBoolean();
                    withCar(main, (car, mainEntity, living) -> car.setHorn(horn));
                }
                case T_SPEED -> {
                    main = in.readInt();
                    float speed = in.readFloat();
                    withCar(main, (car, mainEntity, living) -> car.setActualSpeed(speed));
                }
                case T_CAMERA -> {
                    CameraHandler.yawOffset = in.readFloat();
                    CameraHandler.pitchOffset = in.readFloat();
                }
                case T_CAMERA_RENDERED -> playbackCamera = new CameraFrame(
                        in.readInt(),
                        in.readDouble(), in.readDouble(), in.readDouble(),
                        in.readFloat(), in.readFloat(),
                        in.readFloat(), in.readFloat(),
                        in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(),
                        in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble());
                default -> LOGGER.warn("Unknown recorded Cars packet type: {}", type);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to apply recorded Cars packet", e);
        }
    }

    /**
     * Car.getInstances()/Car.get(...) — точные сигнатуры вызова зависят от того,
     * что реально принимает Car.get(...) (в UdpClientHandler он принимает сам
     * пакет + TriConsumer, потому что достаёт id машины из VehiclePacket).
     * Здесь мы уже знаем "main" id напрямую, так что проще и надёжнее взять
     * машину из статической карты инстансов. Проверь актуальное имя геттера
     * в твоей версии Cars (Car.getInstances() был найден в декомпиле).
     */
    private static void withCar(int mainId, TriConsumer<Car, Entity, ICarLivingEntity> consumer) {
        Car car = Car.getInstances().get(mainId);
        if (car == null) {
            // Машина ещё не создана — это значит, что соответствующий
            // VehicleInfoPacket (обычный vanilla custom payload) ещё не
            // был обработан ReplayMod'ом на этом моменте реплея. Обычно
            // порядок пакетов в .mcpr это гарантирует, но на всякий случай
            // просто пропускаем апдейт вместо краша.
            return;
        }
        Entity mainEntity = car.getMainEntity();
        ICarLivingEntity living = (mainEntity instanceof ICarLivingEntity ice) ? ice : null;
        consumer.accept(car, mainEntity, living);
    }

    private record CameraFrame(int focusedEntityId, double x, double y, double z,
                               float yaw, float pitch, float yawOffset, float pitchOffset,
                               double qx, double qy, double qz, double qw,
                               double pqx, double pqy, double pqz, double pqw) {}
}
