package xpizzax.emoticons.client.renderer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public final class OptionalMods {
    private OptionalMods() {}

    private static final class EmoteApi {
        static final Class<?> TYPE;
        static final MethodHandle PLAYING;

        static {
            Class<?> type = null;
            MethodHandle playing = null;
            try {
                type = Class.forName("io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity");
                playing = MethodHandles.publicLookup().findVirtual(type, "isPlayingEmote",
                        MethodType.methodType(boolean.class))
                        .asType(MethodType.methodType(boolean.class, AbstractClientPlayerEntity.class));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                type = null;
                playing = null;
            }
            TYPE = type;
            PLAYING = playing;
        }
    }

    public static boolean isEmotecraftPlaying(AbstractClientPlayerEntity player) {
        if (player == null || EmoteApi.TYPE == null || EmoteApi.PLAYING == null
                || !EmoteApi.TYPE.isInstance(player)) {
            return false;
        }
        try {
            return (boolean) EmoteApi.PLAYING.invokeExact(player);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
