package studio.pose;

import net.minecraftforge.common.ForgeConfigSpec;


public final class StudioConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_ACTORS;
    public static final ForgeConfigSpec.IntValue MAX_PROPS;
    static {
        ForgeConfigSpec.Builder builder=new ForgeConfigSpec.Builder();
        MAX_ACTORS=builder.comment("Maximum living Studio actors per dimension, including players and armor stands. Non-living props have a separate budget.",
            "Use 5 on modest hardware/heavy shaders, 8 by default, or up to 10 after checking performance.",
            "Ordinary world entities are not hidden or counted by this Studio budget.")
            .defineInRange("maxStudioActors",8,5,10);
        MAX_PROPS=builder.comment("Maximum non-living Studio props per dimension, separate from the living/player budget.","Includes dropped items, vehicles, projectiles and display entities. Check shader performance before using 30.")
            .defineInRange("maxStudioProps",25,20,30);
        SPEC=builder.build();
    }
    public static int maxActors() {return MAX_ACTORS.get();}
    public static int maxProps() {return MAX_PROPS.get();}
    public static boolean prop(net.minecraft.world.entity.Entity e) {return !(e instanceof net.minecraft.world.entity.LivingEntity);}
    public static final int MAX_BATCH=40;
    private StudioConfig() {}
}
