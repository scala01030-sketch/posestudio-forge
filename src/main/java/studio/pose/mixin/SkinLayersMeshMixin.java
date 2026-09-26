package studio.pose.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import studio.pose.mesh.SkinLayerMesh;

/** Optional bridge: absent Skin Layers 3D requires no dependency and no substitute rendering. */
@Pseudo
@Mixin(targets="dev.tr7zw.skinlayers.render.CustomizableModelPart",remap=false)
public abstract class SkinLayersMeshMixin {
    @ModifyVariable(method="compile(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
                    at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false,require=0)
    private VertexConsumer pose$deformSkin(VertexConsumer consumer,ModelPart parent,PoseStack.Pose matrix,
                                         VertexConsumer original,int light,int overlay,int color) {
        return SkinLayerMesh.wrap(parent,consumer);
    }
}
