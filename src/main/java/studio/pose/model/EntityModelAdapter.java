package studio.pose.model;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import studio.pose.mixin.ModelPartAccess;
import studio.pose.mixin.QuadrupedAccess;

/** Only known model containers are inspected; arbitrary mod object graphs are never traversed. */
public final class EntityModelAdapter {
    public record Binding(String name,String parent,boolean playerLimb) {}
    public final IdentityHashMap<ModelPart,Binding> bindings=new IdentityHashMap<>();
    public final Set<ModelPart> skinOverlays=Collections.newSetFromMap(new IdentityHashMap<>());
    public final IdentityHashMap<ModelPart,ModelPart> limbRoots=new IdentityHashMap<>();
    public final IdentityHashMap<ModelPart,ModelPart> skinBases=new IdentityHashMap<>();
    public final Set<String> editableBones=new LinkedHashSet<>();
    private final Set<ModelPart> modelRoots=Collections.newSetFromMap(new IdentityHashMap<>());
    public final boolean player,humanoid;
    public net.minecraft.network.chat.Component status; public boolean ready;
    public EntityModelAdapter(EntityRenderer<?> renderer) {
        player=renderer instanceof LivingEntityRenderer<?,?> l && l.getModel() instanceof PlayerModel<?>;
        humanoid=renderer instanceof LivingEntityRenderer<?,?> l && l.getModel() instanceof HumanoidModel<?>;
        try {
            if(renderer instanceof LivingEntityRenderer<?,?> l) addModel(l.getModel(),player);
            scanContainer(renderer,0);
            buildEditableBones();
            ready=!bindings.isEmpty(); status=net.minecraft.network.chat.Component.translatable(ready?"posestudio.adapter.ready":"posestudio.adapter.unsupported",bindings.size());
        } catch(ReflectiveOperationException | RuntimeException e) { status=net.minecraft.network.chat.Component.translatable("posestudio.adapter.partial",e.getClass().getSimpleName()); }
    }
    private void addModel(Model model,boolean mesh) throws ReflectiveOperationException {
        if(model instanceof QuadrupedModel<?>) {
            QuadrupedAccess q=(QuadrupedAccess)(Object)model;
            tree(q.pose$head(),"head",null,false);tree(q.pose$body(),"body",null,false);
            tree(q.pose$rightHind(),"right_hind_leg","body",false);tree(q.pose$leftHind(),"left_hind_leg","body",false);
            tree(q.pose$rightFront(),"right_front_leg","body",false);tree(q.pose$leftFront(),"left_front_leg","body",false);
        }
        if(model instanceof HumanoidModel<?> h) {
            tree(h.head,"head",null,false);tree(h.hat,"head",null,false);
            tree(h.body,"body",null,false);
            tree(h.leftArm,"left_arm","body",mesh); tree(h.rightArm,"right_arm","body",mesh);
            tree(h.leftLeg,"left_leg","body",mesh); tree(h.rightLeg,"right_leg","body",mesh);
        }
        if(model instanceof HierarchicalModel<?> h) tree(h.root(),"root",null,false);
        if(model instanceof PlayerModel<?> p) {
            skinOverlays.addAll(List.of(p.hat,p.jacket,p.leftSleeve,p.rightSleeve,p.leftPants,p.rightPants));
            skinBases.put(p.hat,p.head);skinBases.put(p.jacket,p.body);
            skinBases.put(p.leftSleeve,p.leftArm);skinBases.put(p.rightSleeve,p.rightArm);
            skinBases.put(p.leftPants,p.leftLeg);skinBases.put(p.rightPants,p.rightLeg);
            tree(p.leftSleeve,"left_arm","body",true);tree(p.rightSleeve,"right_arm","body",true);
            tree(p.leftPants,"left_leg","body",true);tree(p.rightPants,"right_leg","body",true);
            tree(p.jacket,"body",null,false);
        }
        for(Class<?> c=model.getClass();c!=null && c!=Object.class;c=c.getSuperclass()) {
            for(Field f:c.getDeclaredFields()) {
                if(java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !ModelPart.class.isAssignableFrom(f.getType())) continue;
                if(!f.trySetAccessible()) continue;
                ModelPart part=(ModelPart)f.get(model); if(part==null || bindings.containsKey(part)) continue;
                // Explicit skin layer aliases survive obfuscation and keep sleeves/pants on the same pose.
                String name=partName(part,f.getName()); boolean limb=false;
                if(model instanceof PlayerModel<?> p) {
                    if(part==p.leftSleeve) { name="left_arm"; limb=true; }
                    else if(part==p.rightSleeve) { name="right_arm"; limb=true; }
                    else if(part==p.leftPants) { name="left_leg"; limb=true; }
                    else if(part==p.rightPants) { name="right_leg"; limb=true; }
                    else if(part==p.jacket) name="body";
                }
                tree(part,name,null,limb);
            }
        }
    }
    private void scanContainer(Object container,int depth) throws ReflectiveOperationException {
        if(depth>2) return;
        for(Class<?> c=container.getClass();c!=null && c!=Object.class;c=c.getSuperclass()) {
            for(Field f:c.getDeclaredFields()) {
                if(java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Class<?> type=f.getType();
                if(!Model.class.isAssignableFrom(type) && !ModelPart.class.isAssignableFrom(type) && !List.class.isAssignableFrom(type)) continue;
                if(!f.trySetAccessible()) continue; Object value=f.get(container);
                if(value instanceof Model m) addModel(m,player && m instanceof HumanoidModel<?>);
                else if(value instanceof ModelPart part) tree(part,partName(part,f.getName()),null,false);
                else if(value instanceof List<?> list) for(Object element:list) if(element instanceof RenderLayer<?,?>) scanContainer(element,depth+1);
            }
        }
    }
    private static String partName(ModelPart part,String fallback) {
        // Optional EMF names describe its existing vanilla anchors without depending on SRG field names.
        for(Class<?> type=part.getClass();type!=null && type.getName().startsWith("traben.entity_model_features.");type=type.getSuperclass()) {
            try {Field field=type.getDeclaredField("name");if(field.trySetAccessible() && field.get(part) instanceof String name && !name.isBlank()) return name;}
            catch(ReflectiveOperationException | RuntimeException ignored) {}
        }
        return fallback;
    }
    private boolean geometry(ModelPart part,Set<ModelPart> seen) {
        if(!seen.add(part)) return false;
        var access=(ModelPartAccess)(Object)part;
        if(!access.pose$cubes().isEmpty()) return true;
        for(ModelPart child:access.pose$children().values()) if(geometry(child,seen)) return true;
        return false;
    }
    private boolean geometry(ModelPart part) {return geometry(part,Collections.newSetFromMap(new IdentityHashMap<>()));}
    private static String anatomy(String path) {
        String leaf=path.substring(path.lastIndexOf('/')+1).replaceFirst("^EMF_","").replaceFirst("[0-9]+$","").replaceFirst("_rotation$","");
        return leaf.matches("head|body|tail|neck|leg|arm|wing|(left|right)_(arm|leg|wing)|(front|back)_(left|right)_leg|(left|right)_(front|hind)_leg")?leaf:"";
    }
    private void buildEditableBones() {
        for(ModelPart root:modelRoots) if(geometry(root)) editableBones.add(bindings.get(root).name());
        for(var entry:bindings.entrySet()) {
            Binding binding=entry.getValue();String logical=anatomy(binding.name());
            if(logical.isEmpty() || !geometry(entry.getKey())) continue;
            String path=binding.parent();boolean duplicate=false;
            while(path!=null) {
                String ancestor=anatomy(path);
                if(logical.equals(ancestor) || (logical.matches("leg|arm|wing") && ancestor.endsWith("_"+logical))) {duplicate=true;break;}
                int slash=path.lastIndexOf('/');path=slash<0?null:path.substring(0,slash);
            }
            if(!duplicate) editableBones.add(binding.name());
        }
    }
    private void tree(ModelPart part,String name,String parent,boolean limb) {
        if(!bindings.containsKey(part)) modelRoots.add(part);
        tree(part,name,parent,limb,null);
    }
    private void tree(ModelPart part,String name,String parent,boolean limb,ModelPart limbRoot) {
        if(bindings.containsKey(part) || bindings.size()>=512) return;
        if(limb) limbRoot=part;
        if(limbRoot!=null) limbRoots.put(part,limbRoot);
        bindings.put(part,new Binding(name,parent,limb));
        ModelPart inherited=limbRoot;
        ((ModelPartAccess)(Object)part).pose$children().entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(e->tree(e.getValue(),name+"/"+e.getKey(),name,false,inherited));
    }
}
