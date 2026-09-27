package studio.pose.verification.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.model.RenderContext;
import studio.pose.verification.GenderProbe;

@Pseudo
@Mixin(targets="com.wildfire.render.GenderLayer",remap=false)
public abstract class GenderProbeMixin {
    @Inject(method="render",at=@At("HEAD"),require=0,remap=false)
    private void posefixtures$layer(CallbackInfo ci) {GenderProbe.layerCalls++;}
    @Inject(method="renderBreast",at=@At("HEAD"),require=0,remap=false)
    private void posefixtures$matrix(AbstractClientPlayer player,ItemStack item,PoseStack stack,MultiBufferSource buffers,RenderType type,int light,int overlay,float alpha,boolean left,CallbackInfo ci) {
        var frame=RenderContext.current();if(frame!=null && frame.actor()!=null && !frame.shadow()) {GenderProbe.matrix=new org.joml.Matrix4f(stack.last().pose());GenderProbe.calls++;}
    }
}
