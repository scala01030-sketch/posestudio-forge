package studio.pose.mixin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.server.FreezeService;
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true)
    private void pose$freeze(Entity entity,CallbackInfo ci) {
        if(!(entity instanceof ServerPlayer) && FreezeService.frozen(entity)) ci.cancel();
    }
}
