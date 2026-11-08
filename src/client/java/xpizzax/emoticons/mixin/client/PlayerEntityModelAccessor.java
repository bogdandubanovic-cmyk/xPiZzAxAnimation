package xpizzax.emoticons.mixin.client;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(PlayerEntityModel.class)
public interface PlayerEntityModelAccessor {
    @Accessor("thinArms") boolean xpizzax$isThinArms();
}
