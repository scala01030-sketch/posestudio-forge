package studio.pose.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.pose.model.RenderContext;

/** Prevent native swimming/crawl/flight root transforms from tilting the neutral Studio actor. */
@Mixin(LivingEntity.class)
public abstract class NeutralLivingRenderMixin {
    @Inject(method="getSwimAmount",at=@At("HEAD"),cancellable=true)
    private void pose$noSwim(float partial,CallbackInfoReturnable<Float> ci) {
        if(RenderContext.neutralHumanoid((LivingEntity)(Object)this)) ci.setReturnValue(0f);
    }
    @Inject(method="isFallFlying",at=@At("HEAD"),cancellable=true)
    private void pose$noFlight(CallbackInfoReturnable<Boolean> ci) {
        if(RenderContext.neutralHumanoid((LivingEntity)(Object)this)) ci.setReturnValue(false);
    }
}
