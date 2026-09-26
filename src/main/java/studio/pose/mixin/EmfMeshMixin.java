package studio.pose.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** EMF overrides ModelPart.compile as well as render. Keep its geometry and UV implementation. */
@Pseudo
@Mixin(targets="traben.entity_model_features.models.parts.EMFModelPart",remap=false)
public abstract class EmfMeshMixin {
    @ModifyVariable(method={"m_104290_","compile"},at=@At("HEAD"),argsOnly=true,ordinal=0,require=0,remap=false)
    private VertexConsumer pose$customChildMesh(VertexConsumer original) {
        return studio.pose.mesh.SkinLayerMesh.wrapDescendant((ModelPart)(Object)this,original);
    }
}
