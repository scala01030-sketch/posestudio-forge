package studio.pose.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.fml.ModList;
import studio.pose.mixin.ModelPartAccess;


public final class StudioPartRenderer {
    private static final boolean OPTIMIZED=ModList.get().isLoaded("oculus") || ModList.get().isLoaded("embeddium") || ModList.get().isLoaded("rubidium");
    public static boolean render(ModelPart part,PoseStack stack,VertexConsumer vertices,int light,int overlay,float r,float g,float b,float a) {
        if(!OPTIMIZED || RenderContext.binding(part)==null) return false;
        ModelPartAccess access=(ModelPartAccess)(Object)part;
        PoseController.before(part);
        try {
            if(!part.visible || (access.pose$cubes().isEmpty() && access.pose$children().isEmpty())) return true;
            stack.pushPose();
            try {
                part.translateAndRotate(stack);
                if(!part.skipDraw) access.pose$compile(stack.last(),vertices,light,overlay,r,g,b,a);
                for(ModelPart child:access.pose$children().values()) child.render(stack,vertices,light,overlay,r,g,b,a);
            } finally { stack.popPose(); }
        } finally { PoseController.after(part); }
        return true;
    }
    private StudioPartRenderer() {}
}
