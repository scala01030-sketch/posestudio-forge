package studio.pose.mixin;

import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.pose.model.RenderContext;

/** Only the active frozen humanoid renderer sees a standing locomotion baseline. */
@Mixin(Entity.class)
public abstract class NeutralEntityRenderMixin {
    @Inject(method="getPose",at=@At("HEAD"),cancellable=true)
    private void pose$standing(CallbackInfoReturnable<Pose> ci) {
        if(RenderContext.neutralHumanoid((Entity)(Object)this)) ci.setReturnValue(Pose.STANDING);
    }
}
