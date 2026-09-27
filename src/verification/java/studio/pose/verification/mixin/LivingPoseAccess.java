package studio.pose.verification.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingPoseAccess {
    @Accessor("swimAmount") void posefixtures$swim(float value);
    @Accessor("swimAmountO") void posefixtures$oldSwim(float value);
}
