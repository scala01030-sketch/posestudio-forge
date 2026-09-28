package studio.pose.mixin;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.server.FreezeService;


@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method="checkDespawn",at=@At("HEAD"),cancellable=true)
    private void pose$keepActor(CallbackInfo ci) {
        var entity=(Mob)(Object)this;
        if(!entity.level().isClientSide && FreezeService.frozen(entity)) ci.cancel();
    }
}
