package studio.pose.client;

import java.util.*;
import studio.pose.data.*;
import org.joml.Matrix4f;

public final class ActorState {
    public final UUID id;
    public final Map<String,BonePose> bones=new LinkedHashMap<>();
    public java.util.Set<String> editableBones=java.util.Set.of();
    public final Map<String,Node> nodes=new LinkedHashMap<>();
    public ActorTransform transform;
    public boolean frozen;
    public long renderRequestedNanos,lastMainRenderNanos;
    public int bentVertices;
    public final Map<String,Integer> skinLayerVertices=new LinkedHashMap<>();
    public final Map<String,Double> meshSignatures=new LinkedHashMap<>();
    public final Map<String,org.joml.Vector3f> boneScales=new LinkedHashMap<>();
    public final Map<String,Double> skinTransformErrors=new LinkedHashMap<>();
    public boolean adapterReady; public net.minecraft.network.chat.Component adapterStatus=net.minecraft.network.chat.Component.translatable("posestudio.adapter.not_frozen");
    public record Node(String name,String parent,Matrix4f matrix) {}
    public ActorState(UUID id,ActorTransform transform) { this.id=id; this.transform=transform; }
}
