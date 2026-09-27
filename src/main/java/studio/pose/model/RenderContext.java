package studio.pose.model;

import java.util.*;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import studio.pose.client.*;
import studio.pose.data.BonePose;

public final class RenderContext {
    public record Frame(ActorState actor,EntityModelAdapter adapter,boolean shadow,
                        IdentityHashMap<ModelPart,org.joml.Matrix4f> partMatrices) {
        Frame(ActorState actor,EntityModelAdapter adapter,boolean shadow) { this(actor,adapter,shadow,new IdentityHashMap<>()); }
    }
    private static final Map<EntityRenderer<?>,EntityModelAdapter> ADAPTERS=new WeakHashMap<>();
    private static final Deque<Frame> FRAMES=new LinkedList<>();
    public static EntityModelAdapter inspect(Entity entity,ActorState actor) {
        EntityRenderer<?> renderer=net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        EntityModelAdapter adapter=ADAPTERS.computeIfAbsent(renderer,EntityModelAdapter::new);
        if(!actor.frozen || actor.lastMainRenderNanos==0) actor.editableBones=adapter.editableBones;actor.adapterStatus=adapter.status; actor.adapterReady=adapter.ready;
        return adapter;
    }
    public static void begin(Entity entity,EntityRenderer<?> renderer) {
        StudioState s=StudioState.INSTANCE;
        ActorState a=s.active || s.entering()?s.actors.get(entity.getUUID()):null;
        if(a==null || !a.frozen) { FRAMES.push(new Frame(null,null,false)); return; }
        EntityModelAdapter adapter=inspect(entity,a);
        boolean shadow=ShaderPass.shadow();
        if(!shadow) { a.nodes.clear(); a.bentVertices=0; a.skinLayerVertices.clear();a.meshSignatures.clear();a.skinTransformErrors.clear(); }
        if(adapter.player) for(String name:List.of("left_elbow","right_elbow","left_knee","right_knee")) a.bones.computeIfAbsent(name,k->new BonePose());
        FRAMES.push(new Frame(a,adapter,shadow));
    }
    public static void end() {
        if(FRAMES.isEmpty()) return;
        Frame f=FRAMES.pop();
        if(f.actor()!=null && !f.shadow() && !f.actor().nodes.isEmpty()) {
            java.util.Set<String> geometry=new java.util.HashSet<>();
            for(ModelPart part:f.partMatrices().keySet()) {
                if(!part.visible || part.skipDraw || ((studio.pose.mixin.ModelPartAccess)(Object)part).pose$cubes().isEmpty()) continue;
                var binding=f.adapter().bindings.get(part);if(binding==null) continue;String name=binding.name();
                while(name!=null) {geometry.add(name);int slash=name.lastIndexOf('/');name=slash<0?null:name.substring(0,slash);}
            }
            java.util.Set<String> editable=new java.util.LinkedHashSet<>();
            for(String name:f.adapter().editableBones) if(geometry.contains(name)) editable.add(name);
            f.actor().editableBones=editable;f.actor().lastMainRenderNanos=System.nanoTime();
        }
    }
    public static Frame current() { return FRAMES.isEmpty()?null:FRAMES.peek(); }
    public static boolean neutralHumanoid(Entity entity) {
        if(!entity.level().isClientSide) return false;
        Frame f=current();return f!=null && f.actor()!=null && f.actor().frozen && f.adapter().humanoid && f.actor().id.equals(entity.getUUID());
    }
    public static EntityModelAdapter.Binding binding(ModelPart part) {
        Frame f=current(); return f==null || f.adapter==null?null:f.adapter.bindings.get(part);
    }
    public static void clear() { FRAMES.clear(); ADAPTERS.clear(); }
}
