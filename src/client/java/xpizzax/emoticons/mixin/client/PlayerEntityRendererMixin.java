package xpizzax.emoticons.mixin.client;
import xpizzax.emoticons.client.renderer.*;
import net.example.replaynv.ReplayState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
 @Inject(method="<init>",at=@At("TAIL"))
 private void xpizzax$accessories(EntityRendererFactory.Context context,boolean slim,CallbackInfo ci){
  PlayerEntityRenderer renderer=(PlayerEntityRenderer)(Object)this;
  ((LivingEntityRendererFeaturesAccessor)renderer).xpizzax$getFeatures().add(new RpmBodyAccessoryFeatureRenderer(renderer));
 }
 @Inject(method="render",at=@At("HEAD"),cancellable=true)
 private void xpizzax$begin(AbstractClientPlayerEntity player,float yaw,float delta,MatrixStack stack,VertexConsumerProvider vertices,int light,CallbackInfo ci){
  if(xpizzax.emoticons.client.PhotoItemCompatibility.hideFirstPersonBody(player)){ci.cancel();return;}
  if(ReplayState.isReplayOpen()&&MinecraftClient.getInstance().getCameraEntity()==player
    &&!xpizzax.emoticons.ui.emoticons.EmoticonsScreen.isMenuPreview()){ci.cancel();return;}
  BackAccessoryRenderScope.begin(player);
  VanillaEmoticonsBridge.begin(player,(PlayerEntityRenderer)(Object)this,delta);
 }
 @Inject(method="render",at=@At("RETURN"))
 private void xpizzax$end(AbstractClientPlayerEntity player,float yaw,float delta,MatrixStack stack,VertexConsumerProvider vertices,int light,CallbackInfo ci){
  try { VanillaEmoticonsBridge.end(player,(PlayerEntityRenderer)(Object)this); }
  finally { BackAccessoryRenderScope.end(player); }
 }
}
