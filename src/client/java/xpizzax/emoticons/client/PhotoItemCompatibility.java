package xpizzax.emoticons.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;

/** RPM photographs are filled maps; their name, map ID and author can change. */
public final class PhotoItemCompatibility {
    private static final ThreadLocal<Boolean> CHECKING = ThreadLocal.withInitial(() -> false);
    private PhotoItemCompatibility() {}

    public static boolean isPhoto(ItemStack item) {
        return item != null && !item.isEmpty() && item.getItem() instanceof FilledMapItem;
    }

    /** Hide only the extra world-model pass, leaving vanilla's photo and hand. */
    public static boolean hideFirstPersonBody(LivingEntity entity) {
        if (!(entity instanceof PlayerEntity player) || CHECKING.get()) return false;
        CHECKING.set(true);
        try {
            // FirstPerson filters equipment getters. TaCZ also reads equipment
            // from getPerspective, which can call this policy again. Read the
            // real inventory first and leave nested queries on the native path.
            var inventory = player.getInventory();
            ItemStack left = player.getMainArm() == Arm.LEFT
                    ? inventory.getMainHandStack() : inventory.offHand.get(0);
            if (!isPhoto(left)) return false;
            MinecraftClient client = MinecraftClient.getInstance();
            return client.getCameraEntity() == entity && !VanillaEmoticonsBridge.isPreview()
                    && client.options.getPerspective().isFirstPerson();
        } finally {
            CHECKING.set(false);
        }
    }
}
