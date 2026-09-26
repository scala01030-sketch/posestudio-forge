package studio.pose.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.client.StudioState;
@Mixin(Minecraft.class)
public abstract class MinecraftInputMixin {
    @Inject(method="handleKeybinds",at=@At("HEAD"),cancellable=true)
    private void pose$cameraControls(CallbackInfo ci) {
        if(!StudioState.INSTANCE.active) return;
        // Camera Q/E and mouse buttons must not drop items, open inventory or use the player's hands.
        for(var key:((Minecraft)(Object)this).options.keyMappings) {
            if(key.getName().startsWith("key.posestudio.")) continue;
            while(key.consumeClick()) { /* discard gameplay actions while the camera owns input */ }
        }
        ci.cancel();
    }
}
