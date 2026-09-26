package studio.pose.mixin;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.client.StudioState;
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void pose$returnGuide(float partial,long finishTime,boolean renderLevel,CallbackInfo ci) {
        if(renderLevel) studio.pose.overlay.StudioHud.draw();
    }
    @Inject(method={"bobView","bobHurt"},at=@At("HEAD"),cancellable=true)
    private void pose$steady(CallbackInfo ci) { if(StudioState.INSTANCE.active) ci.cancel(); }
}
