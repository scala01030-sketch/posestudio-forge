package studio.pose.mixin;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
@Mixin(ModelPart.class)
public interface ModelPartAccess {
    @Accessor("children") Map<String,ModelPart> pose$children();
    @Accessor("cubes") List<ModelPart.Cube> pose$cubes();
    @Invoker("compile") void pose$compile(PoseStack.Pose pose,VertexConsumer consumer,int light,int overlay,float r,float g,float b,float a);
}
