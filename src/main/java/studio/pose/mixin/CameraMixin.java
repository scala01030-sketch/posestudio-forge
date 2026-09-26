package studio.pose.mixin;
import net.minecraft.client.Camera;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.client.StudioState;
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(double x,double y,double z);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Shadow private boolean detached;
    @Inject(method="setup",at=@At("RETURN"))
    private void pose$camera(BlockGetter world,Entity entity,boolean third,boolean reverse,float partial,CallbackInfo ci) {
        if(StudioState.INSTANCE.active) {
            var c=StudioState.INSTANCE.camera; detached=true; setPosition(c.x,c.y,c.z); setRotation(c.yaw,c.pitch);
        }
    }
}
