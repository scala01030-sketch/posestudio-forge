package studio.pose.mixin;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.client.StudioState;
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Inject(method="onPress",at=@At("HEAD"),cancellable=true)
    private void pose$mouseKeys(long window,int button,int action,int modifiers,CallbackInfo ci) {
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(window==mc.getWindow().getWindow() && action==org.lwjgl.glfw.GLFW.GLFW_PRESS && (mc.screen==null || mc.screen instanceof studio.pose.ui.StudioScreen) && studio.pose.client.StudioKeys.mousePress(button)) ci.cancel();
    }
    @Redirect(method="turnPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void pose$look(LocalPlayer p,double x,double y) {
        if(StudioState.INSTANCE.active) StudioState.INSTANCE.camera.lookAdjusted(x,y);
        else p.turn(x,y);
    }
}
