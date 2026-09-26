package studio.pose.verification;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** Independent mod renderer with a named hierarchy and continuously written idle animation. */
public final class FixtureRenderer extends MobRenderer<Fixtures.FixturePig,FixtureRenderer.FixtureModel> {
    public FixtureRenderer(EntityRendererProvider.Context context) { super(context,new FixtureModel(context.bakeLayer(ModelLayers.PIG)),.6f); }
    @Override public ResourceLocation getTextureLocation(Fixtures.FixturePig entity) { return new ResourceLocation("minecraft","textures/entity/pig/pig.png"); }
    public static final class FixtureModel extends HierarchicalModel<Fixtures.FixturePig> {
        private final ModelPart root;
        public FixtureModel(ModelPart root) { this.root=root; }
        @Override public ModelPart root() { return root; }
        @Override public void setupAnim(Fixtures.FixturePig entity,float walk,float amount,float age,float yaw,float pitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            root.getChild("head").xRot=(float)Math.sin(age*.12)*.25f;
            root.getChild("head").yRot=yaw*(float)Math.PI/180;
        }
    }
}
