package studio.pose;

import net.minecraftforge.fml.common.Mod;
import studio.pose.network.StudioNetwork;

@Mod(PoseStudio.ID)
public final class PoseStudio {
    public static final String ID = "posestudio";
    public PoseStudio() { net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,StudioConfig.SPEC);StudioNetwork.register(); }
}
