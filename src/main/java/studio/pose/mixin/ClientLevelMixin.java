package studio.pose.mixin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.client.StudioState;
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true)
    private void pose$freeze(Entity e,CallbackInfo ci) {
        var s=StudioState.INSTANCE; var a=s.actors.get(e.getUUID());
        if((s.active || s.entering()) && a!=null && a.frozen && e!=net.minecraft.client.Minecraft.getInstance().player) ci.cancel();
    }
}
