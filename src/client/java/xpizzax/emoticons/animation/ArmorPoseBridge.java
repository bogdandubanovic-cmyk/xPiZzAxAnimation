package xpizzax.emoticons.animation;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import dev.kosmx.playerAnim.impl.IMutableModel;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.impl.animation.IBendHelper;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.SetableSupplier;

/** Called after vanilla/Dynamix copyBipedStateTo, for the actual inner/outer armor model. */
public final class ArmorPoseBridge {
    // Render-thread only; weak keys never retain discarded renderer/model caches.
    private static final java.util.Set<BipedEntityModel<?>> TOUCHED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private ArmorPoseBridge() {}
    public static void apply(BipedEntityModel<?> armor, AnimationApplier processor,
            SetableSupplier<AnimationProcessor> supplier) {
        if (armor == null || !((Object) armor instanceof IMutableModel mutable)) return;
        if (processor == null || supplier == null) {
            if (!TOUCHED.contains(armor)) return;
            // A reused armor model can still contain Bendy geometry from the previous
            // player. Native copyTransform copies pose/scale, not that geometry.
            var inherited = mutable.getEmoteSupplier();
            var inheritedProcessor = inherited == null ? null : inherited.get();
            if (inheritedProcessor == null || !inheritedProcessor.isActive()) {
                IBendHelper.INSTANCE.bend(armor.body, 0F, 0F);
                IBendHelper.INSTANCE.bend(armor.rightArm, 0F, 0F);
                IBendHelper.INSTANCE.bend(armor.leftArm, 0F, 0F);
                IBendHelper.INSTANCE.bend(armor.rightLeg, 0F, 0F);
                IBendHelper.INSTANCE.bend(armor.leftLeg, 0F, 0F);
                TOUCHED.remove(armor);
            }
            return;
        }
        TOUCHED.add(armor);
        mutable.setEmoteSupplier(supplier);
        processor.updatePart("head", armor.head);
        armor.hat.copyTransform(armor.head);
        processor.updatePart("torso", armor.body);
        processor.updatePart("rightArm", armor.rightArm);
        processor.updatePart("leftArm", armor.leftArm);
        processor.updatePart("rightLeg", armor.rightLeg);
        processor.updatePart("leftLeg", armor.leftLeg);
        // Visibility and textures remain controlled by ArmorFeatureRenderer's slot.
    }
}
