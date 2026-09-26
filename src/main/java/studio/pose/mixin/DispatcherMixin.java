package studio.pose.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.model.RenderContext;
@Mixin(EntityRenderDispatcher.class)
public abstract class DispatcherMixin {
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void pose$orientation(net.minecraft.client.renderer.entity.EntityRenderer renderer,Entity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        var actor=studio.pose.client.StudioState.INSTANCE.actors.get(entity.getUUID());
        boolean frozen=studio.pose.client.StudioState.INSTANCE.active && actor!=null && actor.frozen;
        studio.pose.model.ActorOrientation.render(entity,stack,()->renderer.render(entity,frozen?0:yaw,frozen?0:partial,stack,buffers,light));
    }
    @Inject(method="render",at=@At("HEAD"))
    private void pose$begin(Entity e,double x,double y,double z,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light,CallbackInfo ci) {
        studio.pose.client.VisibleActors.rendered(e,x,y,z,yaw,partial);
        RenderContext.begin(e,((EntityRenderDispatcher)(Object)this).getRenderer(e));
    }
    @Inject(method="render",at=@At("RETURN"))
    private void pose$end(CallbackInfo ci) { RenderContext.end(); }
}
