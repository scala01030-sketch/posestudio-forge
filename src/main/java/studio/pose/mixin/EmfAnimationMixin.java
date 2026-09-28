package studio.pose.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.model.RenderContext;


@Pseudo
@Mixin(targets="traben.entity_model_features.models.parts.EMFModelPartRoot",remap=false)
public abstract class EmfAnimationMixin {
    @Inject(method="animate",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void pose$freezeAnimation(CallbackInfo ci) {
        var frame=RenderContext.current();
        if(frame!=null && frame.actor()!=null && frame.actor().frozen) ci.cancel();
    }
}
