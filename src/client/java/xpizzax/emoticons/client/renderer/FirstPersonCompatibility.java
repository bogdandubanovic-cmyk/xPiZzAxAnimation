package xpizzax.emoticons.client.renderer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

/** The camera player's body pass, independently of mouse capture and GUI focus. */
public final class FirstPersonCompatibility {
    private static final MethodHandle FIRST_PERSON_PASS = findBodyPass();

    private FirstPersonCompatibility() {}

    private static MethodHandle findBodyPass() {
        try {
            Class<?> api = Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI");
            return MethodHandles.publicLookup().findStatic(api, "isRenderingPlayer",
                    MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError absent) {
            return null;
        }
    }

    public static boolean isBodyPass(LivingEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity == null || entity != client.getCameraEntity() || VanillaEmoticonsBridge.isPreview()) return false;
        if (FIRST_PERSON_PASS != null) {
            try { return (boolean) FIRST_PERSON_PASS.invokeExact(); }
            catch (Throwable unavailable) { return false; }
        }
        // Body-rendering mods without FirstPerson's API still render the camera
        // entity in first person. Inventory/model previews have their own scope.
        return client.options.getPerspective().isFirstPerson();
    }
}
