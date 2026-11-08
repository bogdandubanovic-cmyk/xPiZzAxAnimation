package xpizzax.emoticons.mixin.client;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
/** Runs our post-merge plugin on PlayerEntity after RPM's priority-1000 contribution. */
@Mixin(value=PlayerEntity.class,priority=900)
public abstract class ReplayGenderPlayerMixin {}
