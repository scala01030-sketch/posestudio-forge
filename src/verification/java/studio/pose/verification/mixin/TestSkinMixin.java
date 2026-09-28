package studio.pose.verification.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(AbstractClientPlayer.class)
public abstract class TestSkinMixin {
    @Inject(method="getSkinTextureLocation",at=@At("HEAD"),cancellable=true)
    private void skin(CallbackInfoReturnable<ResourceLocation> ci) {
        if(Boolean.getBoolean("posestudio.acceptance")) ci.setReturnValue(new ResourceLocation("posefixtures","textures/test_skin.png"));
    }
    @Inject(method="getModelName",at=@At("HEAD"),cancellable=true)
    private void arms(CallbackInfoReturnable<String> ci) {
        if(Boolean.getBoolean("posestudio.acceptance")) ci.setReturnValue(System.getProperty("posestudio.acceptance.skin","default"));
    }
}
