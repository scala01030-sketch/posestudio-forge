package studio.pose.verification;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.renderer.entity.PigRenderer;

@Mod("posefixtures")
public final class Fixtures {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"posefixtures");
    public static final RegistryObject<EntityType<FixturePig>> PIG=TYPES.register("modelpart_pig",()->EntityType.Builder.of(FixturePig::new,MobCategory.CREATURE).sized(.9f,.9f).build("posefixtures:modelpart_pig"));
    public static class FixturePig extends Pig { public FixturePig(EntityType<? extends Pig> type,Level level) { super(type,level); } }
    public Fixtures() {
        var bus=FMLJavaModLoadingContext.get().getModEventBus();TYPES.register(bus);
        bus.addListener((EntityAttributeCreationEvent e)->e.put(PIG.get(),Pig.createAttributes().build()));
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->e.registerEntityRenderer(PIG.get(),FixtureRenderer::new));
        MinecraftForge.EVENT_BUS.register(Boolean.getBoolean("posestudio.acceptance.placement")?new PlacementReadyRun():Boolean.getBoolean("posestudio.acceptance.items")?new ItemCatalogRun():Boolean.getBoolean("posestudio.acceptance.usability")?new UsabilityRun():Boolean.getBoolean("posestudio.acceptance.entry")?new EntryMoveRun():Boolean.getBoolean("posestudio.acceptance.diverse")?new DiverseModelsRun():new AcceptanceRun());
    }
}
