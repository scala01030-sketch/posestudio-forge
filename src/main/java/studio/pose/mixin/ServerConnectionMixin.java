package studio.pose.mixin;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.pose.server.FreezeService;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerConnectionMixin {
    @Shadow public ServerPlayer player;
    @Inject(method={"handleMovePlayer","handlePlayerInput","handlePlayerAction","handleUseItem","handleUseItemOn","handleInteract"},at=@At("HEAD"),cancellable=true)
    private void pose$stopInput(CallbackInfo ci) { if(player.server.isSameThread() && FreezeService.frozen(player)) ci.cancel(); }
}
