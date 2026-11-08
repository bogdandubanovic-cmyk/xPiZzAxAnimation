package xpizzax.emoticons.mixin.client;
import java.util.ArrayDeque;
import xpizzax.emoticons.client.renderer.VanillaEmoticonsBridge;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = {"com.wildfire.render.GenderLayer", "com.wildfire.render.GenderArmorLayer"}, remap = false)
public abstract class WildfireVanillaBendMixin {
    @Unique private final ArrayDeque<Boolean> xpizzax$chestScopes = new ArrayDeque<>();
    @Inject(method = "render", at = @At("HEAD"), remap = false, require = 0)
    private void xpizzax$chestBegin(MatrixStack stack, VertexConsumerProvider consumers, int light,
            LivingEntity entity, float limb, float amount, float delta, float age,
            float yaw, float pitch, CallbackInfo ci) {
        this.xpizzax$chestScopes.push(VanillaEmoticonsBridge.chest(entity, stack));
    }
    @Inject(method = "render", at = @At("RETURN"), remap = false, require = 0)
    private void xpizzax$chestEnd(MatrixStack stack, VertexConsumerProvider consumers, int light,
            LivingEntity entity, float limb, float amount, float delta, float age,
            float yaw, float pitch, CallbackInfo ci) {
        if (!this.xpizzax$chestScopes.isEmpty() && this.xpizzax$chestScopes.pop()) stack.pop();
    }
}
