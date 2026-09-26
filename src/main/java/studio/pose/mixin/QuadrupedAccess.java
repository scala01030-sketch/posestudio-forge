package studio.pose.mixin;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(QuadrupedModel.class)
public interface QuadrupedAccess {
    @Accessor("head") ModelPart pose$head();
    @Accessor("body") ModelPart pose$body();
    @Accessor("rightHindLeg") ModelPart pose$rightHind();
    @Accessor("leftHindLeg") ModelPart pose$leftHind();
    @Accessor("rightFrontLeg") ModelPart pose$rightFront();
    @Accessor("leftFrontLeg") ModelPart pose$leftFront();
}
