package studio.pose.mixin;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.model.PoseController;
import studio.pose.mesh.PlayerLimbMesh;
// Applied after the optimizer mixins so this HEAD guard precedes their cancellable HEAD hooks.
@Mixin(value=ModelPart.class,priority=900)
public abstract class ModelPartMixin {
    private static final String RENDER="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";
    @Inject(method=RENDER,at=@At("HEAD"),cancellable=true)
    private void pose$apply(PoseStack stack,VertexConsumer vertices,int light,int overlay,float r,float g,float b,float a,CallbackInfo ci) {
        ModelPart part=(ModelPart)(Object)this;
        if(studio.pose.model.StudioPartRenderer.render(part,stack,vertices,light,overlay,r,g,b,a)) ci.cancel();
        else PoseController.before(part);
    }
    @Inject(method=RENDER,at=@At("RETURN"))
    private void pose$restore(CallbackInfo ci) { PoseController.after((ModelPart)(Object)this); }
    @Inject(method="translateAndRotate",at=@At("HEAD"))
    private void pose$attachmentPose(CallbackInfo ci) { PoseController.before((ModelPart)(Object)this); }
    @Inject(method="translateAndRotate",at=@At("RETURN"))
    private void pose$attachmentRestore(PoseStack stack,CallbackInfo ci) {
        // Subclasses such as EMF bypass ModelPart.render, but still use this transform.
        PoseController.node((ModelPart)(Object)this,stack);
        PoseController.after((ModelPart)(Object)this);
    }
    @Inject(method="compile",at=@At("HEAD"),cancellable=true)
    private void pose$mesh(PoseStack.Pose p,VertexConsumer v,int light,int overlay,float r,float g,float b,float a,CallbackInfo ci) {
        if(PlayerLimbMesh.render((ModelPart)(Object)this,p,v,light,overlay,r,g,b,a)) ci.cancel();
    }
    @ModifyVariable(method="compile",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private VertexConsumer pose$inheritedLimb(VertexConsumer consumer) {
        return studio.pose.mesh.SkinLayerMesh.wrapDescendant((ModelPart)(Object)this,consumer);
    }
}
