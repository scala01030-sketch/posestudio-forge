package studio.pose.mixin;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.model.*;


@Pseudo
@Mixin(targets="com.wildfire.render.GenderLayer",remap=false)
public abstract class GenderLayerStudioMixin extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    @Unique private final Deque<ModelPart> pose$bodyFrames=new LinkedList<>();
    protected GenderLayerStudioMixin(RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent) {super(parent);}
    @Inject(method="render",at=@At("HEAD"),require=0,remap=false)
    private void pose$body(PoseStack stack,MultiBufferSource buffers,int light,AbstractClientPlayer player,float a,float b,float c,float d,float e,float f,CallbackInfo ci) {
        ModelPart body=getParentModel().body;boolean bound=RenderContext.binding(body)!=null;pose$bodyFrames.push(bound?body:null);if(bound) PoseController.before(body);
    }
    @Inject(method="render",at=@At("RETURN"),require=0,remap=false)
    private void pose$restore(CallbackInfo ci) {if(!pose$bodyFrames.isEmpty()) {ModelPart body=pose$bodyFrames.pop();if(body!=null) PoseController.after(body);}}
    @ModifyVariable(method="renderBreastWithTransforms",at=@At("HEAD"),argsOnly=true,index=22,require=0,remap=false)
    private boolean pose$noBreathing(boolean value) {var frame=RenderContext.current();return frame!=null && frame.actor()!=null?false:value;}
}
