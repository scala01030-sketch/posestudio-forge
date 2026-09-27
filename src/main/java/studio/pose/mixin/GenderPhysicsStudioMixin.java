package studio.pose.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.client.StudioState;

/** Freeze the optional layer's existing simulation without changing its configuration. */
@Pseudo
@Mixin(targets="com.wildfire.physics.BreastPhysics",remap=false)
public abstract class GenderPhysicsStudioMixin {
    @Inject(method="update",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void pose$freeze(Player player,@Coerce Object armor,CallbackInfo ci) {
        var actor=StudioState.INSTANCE.actors.get(player.getUUID());
        if(player.level().isClientSide && StudioState.INSTANCE.active && actor!=null && actor.frozen) ci.cancel();
    }
}
