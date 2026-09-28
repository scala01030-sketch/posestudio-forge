package studio.pose.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.client.StudioKeys;
import studio.pose.ui.StudioScreen;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void pose$studioKeys(long window,int key,int scan,int action,int modifiers,CallbackInfo ci) {
        Minecraft mc=Minecraft.getInstance();
        if(window!=mc.getWindow().getWindow() || (mc.screen!=null && !(mc.screen instanceof StudioScreen))) return;
        if(action==GLFW.GLFW_REPEAT && (StudioKeys.mapped(key,scan) || (key==GLFW.GLFW_KEY_ESCAPE && studio.pose.client.StudioState.INSTANCE.active))) {ci.cancel();return;}
        if(action!=GLFW.GLFW_PRESS) return;

        if(StudioKeys.press(key,scan)) ci.cancel();
    }
}
