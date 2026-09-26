package studio.pose.mixin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.client.StudioState;

/** Removing a temporary photo actor is not a death or a ragdoll event. */
@Pseudo
@Mixin(targets="net.diebuddies.physics.PhysicsMod",remap=false)
public abstract class PhysicsStudioMixin {
    @Inject(method="blockifyEntity",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void pose$noRagdoll(Level level,LivingEntity entity,CallbackInfo ci) {
        if(StudioState.INSTANCE.suppressesPhysics(entity.getUUID())) ci.cancel();
    }
}
