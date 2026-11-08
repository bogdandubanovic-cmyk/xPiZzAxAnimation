package xpizzax.emoticons.client.replay;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.UUID;

/** Optional Wildfire 1.20.4-3.2.1 profile codec. Uses the mod's own sync format;
 * never writes the user's wardrobe files or replaces its global player cache. */
final class WildfireReplayCodec {
    private WildfireReplayCodec() {}

    static JsonObject capture(UUID uuid) throws Exception {
        Class<?> gender;
        try { gender = Class.forName("com.wildfire.main.WildfireGender"); }
        catch (ClassNotFoundException absent) { return null; }
        Object config = gender.getMethod("getPlayerById", UUID.class).invoke(null, uuid);
        JsonObject state = new JsonObject();
        if (config == null) return state;
        Class<?> playerType = Class.forName("com.wildfire.main.entitydata.PlayerConfig");
        Class<?> packetType = Class.forName("com.wildfire.main.networking.SyncPacket");
        Constructor<?> constructor = packetType.getDeclaredConstructor(playerType);
        constructor.setAccessible(true);
        Object packet = constructor.newInstance(config);
        Method getPacket = packetType.getDeclaredMethod("getPacket");
        getPacket.setAccessible(true);
        ByteBuf buffer = (ByteBuf) getPacket.invoke(packet);
        try {
            if (buffer.readableBytes() <= 0 || buffer.readableBytes() > 4096)
                throw new IOException("Invalid Wildfire profile length");
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            state.addProperty("codec", "wildfire-3.2.1");
            state.addProperty("data", Base64.getEncoder().encodeToString(bytes));
            return state;
        } finally { buffer.release(); }
    }

    static Object restore(UUID uuid, JsonObject state) throws Exception {
        if (!state.has("codec") || !"wildfire-3.2.1".equals(state.get("codec").getAsString()))
            throw new IOException("Unsupported Wildfire profile codec");
        byte[] bytes = Base64.getDecoder().decode(state.get("data").getAsString());
        if (bytes.length == 0 || bytes.length > 4096)
            throw new IOException("Invalid Wildfire profile length");
        Class<?> playerType = Class.forName("com.wildfire.main.entitydata.PlayerConfig");
        Class<?> packetType = Class.forName("com.wildfire.main.networking.SyncPacket");
        Constructor<?> constructor = packetType.getDeclaredConstructor(PacketByteBuf.class);
        constructor.setAccessible(true);
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            Object packet = constructor.newInstance(buffer);
            if (buffer.isReadable()) throw new IOException("Unexpected Wildfire profile data");
            java.lang.reflect.Field packetUuid = packetType.getDeclaredField("uuid");
            packetUuid.setAccessible(true);
            if (!uuid.equals(packetUuid.get(packet))) throw new IOException("Wildfire profile UUID mismatch");
            Object profile = playerType.getConstructor(UUID.class).newInstance(uuid);
            Method apply = packetType.getDeclaredMethod("updatePlayerFromPacket", playerType);
            apply.setAccessible(true);
            apply.invoke(packet, profile);
            return profile;
        } finally { buffer.release(); }
    }
}
