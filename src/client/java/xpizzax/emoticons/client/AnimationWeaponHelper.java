package xpizzax.emoticons.client;


import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Arm;

/** Weapon ownership stays with vanilla/RPM; this only gates our pose bridge. */
public final class AnimationWeaponHelper {
    private AnimationWeaponHelper() {}
    private static final TagKey<Item> EXTRA = TagKey.of(RegistryKeys.ITEM, new Identifier("bbs", "animation_weapons"));
    private static final TagKey<Item> COMMON = TagKey.of(RegistryKeys.ITEM, new Identifier("c", "weapons"));
    private static final TagKey<Item> RANGED = TagKey.of(RegistryKeys.ITEM, new Identifier("c", "ranged_weapons"));
    private static final ClassValue<Boolean> MOD_GUN = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return isGunType(type);
        }
    };
    private static boolean isGunType(Class<?> type) {
        if (type == null) return false;
        String name = type.getName();
        if ("com.tacz.guns.api.item.IGun".equals(name)
                || "com.tacz.guns.api.item.gun.AbstractGunItem".equals(name)
                || "com.tacz.guns.item.ModernKineticGunItem".equals(name)) return true;
        for (Class<?> api : type.getInterfaces()) if (isGunType(api)) return true;
        return isGunType(type.getSuperclass());
    }
    public static boolean isHoldingWeapon(LivingEntity entity) {
        // The left physical hand may hold a back accessory without disabling bends.
        // Respect the player's selected main arm, including left-handed players.
        return isWeapon(entity.getMainArm() == Arm.RIGHT
                ? entity.getMainHandStack() : entity.getOffHandStack());
    }
    public static boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        Item item = stack.getItem();

        // Настоящее оружие TACZ/RPMGuns сохраняет свой фикс.
        if (MOD_GUN.get(item.getClass())) {
            return true;
        }

        // Включая RPM-биты, ножи и другие модели на основе инструментов.
        if (item instanceof net.minecraft.item.ToolItem) {
            return false;
        }

        return item instanceof RangedWeaponItem
                || item instanceof TridentItem
                || stack.isIn(EXTRA)
                || stack.isIn(COMMON)
                || stack.isIn(RANGED);
    }
}
