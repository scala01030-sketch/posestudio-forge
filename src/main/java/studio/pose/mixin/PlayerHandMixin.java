package studio.pose.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.model.RenderContext;
import studio.pose.mesh.BendMath;
@Mixin(PlayerModel.class)
public abstract class PlayerHandMixin {
    @Inject(method="translateToHand",at=@At("RETURN"))
    private void pose$wrist(HumanoidArm arm,PoseStack stack,CallbackInfo ci) {
        var frame=RenderContext.current();if(frame==null || frame.actor()==null || !frame.adapter().player) return;
        var joint=frame.actor().bones.get(arm==HumanoidArm.LEFT?"left_elbow":"right_elbow");if(joint==null) return;
        BendMath bend=new BendMath(joint.rotation,4,4);var end=bend.center(10);
        stack.translate(end.x/16,end.y/16,end.z/16);stack.mulPose(bend.orientation(10));stack.translate(0,-10.0/16,0);
    }
}
