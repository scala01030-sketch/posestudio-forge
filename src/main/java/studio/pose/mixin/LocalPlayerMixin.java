package studio.pose.mixin;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.client.StudioState;
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/Input;tick(ZF)V",shift=At.Shift.AFTER))
    private void pose$input(CallbackInfo ci) {
        if(!StudioState.INSTANCE.active && !StudioState.INSTANCE.entering()) return;
        var p=(LocalPlayer)(Object)this; p.input.forwardImpulse=0; p.input.leftImpulse=0;
        p.input.jumping=false; p.input.shiftKeyDown=false;
        p.input.up=p.input.down=p.input.left=p.input.right=false;
    }
}
