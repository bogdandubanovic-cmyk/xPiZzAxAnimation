package xpizzax.emoticons.client.renderer;

import xpizzax.emoticons.client.RpmAccessoryHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public class RpmBodyAccessoryFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>
{
    public RpmBodyAccessoryFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context)
    {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch)
    {
        // Back accessories are torso-mounted even when no RPM pose is active.
        // With RPM/Bendy pose, use the animated chest transform; otherwise use
        // the vanilla body part as a stable fallback. Never leave a back item
        // attached to the offhand just because the animation bridge is idle.
        ItemStack item = player.getEquippedStack(EquipmentSlot.OFFHAND);
        if (!BackAccessoryRenderScope.isBodyAccessory(player, item)) return;

        if (!BackAccessoryRenderScope.beginAccessory(player)) return;
        matrices.push();
        try {
        if (VanillaEmoticonsBridge.hasPose(player))
            VanillaEmoticonsBridge.accessoryChest(player, matrices);
        this.getContextModel().body.rotate(matrices);

        // Preserve the original pack placement for every existing accessory.
        // The RP 1.43.3 tool belt is the exception: its model is authored as a
        // waist mesh, so it must not inherit THIRD_PERSON_LEFT_HAND offsets.
        boolean toolBelt = RpmAccessoryHelper.isToolBelt(item);
        ModelTransformationMode mode;
        if (toolBelt) {
            RpmAccessoryHelper.applyToolBeltTransform(matrices);
            mode = ModelTransformationMode.NONE;
        } else {
            if (item.isOf(net.minecraft.item.Items.GOLDEN_HORSE_ARMOR))
                RpmAccessoryHelper.applyBodyAccessoryTransform(matrices);
            else RpmAccessoryHelper.applyPackedToolBodyTransform(matrices);
            mode = ModelTransformationMode.THIRD_PERSON_LEFT_HAND;
        }

        // The item is equipped in EquipmentSlot.OFFHAND. Keep the actual left-hand
        // display transform for every RP accessory; passing false here silently
        // selected right-handed mirroring and flipped asymmetric CIT/Dynamix models.
        MinecraftClient.getInstance().getItemRenderer().renderItem(player, item,
                mode, true, matrices, vertices, player.getWorld(), light, OverlayTexture.DEFAULT_UV, 0);
        } finally {
            matrices.pop();
            BackAccessoryRenderScope.endAccessory(player);
        }
    }
}
