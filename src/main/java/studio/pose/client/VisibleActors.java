package studio.pose.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import studio.pose.data.ActorTransform;
import studio.pose.network.StudioNetwork.Entry;

/** Collect the actual main world render submissions, never GUI previews or shader shadows. */
public final class VisibleActors {
    private static final Map<UUID,Entry> building=new LinkedHashMap<>();
    private static List<Entry> completed=List.of();
    private static Object level;
    private static long frame;
    private static boolean collecting;
    private static final Map<Class<?>,Optional<java.lang.reflect.Method>> cullingMethods=new HashMap<>();
    public static void stage(RenderLevelStageEvent e) {
        if(studio.pose.model.ShaderPass.shadow()) return;
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY) {building.clear();collecting=true;}
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            collecting=false;completed=List.copyOf(building.values());level=Minecraft.getInstance().level;frame=System.nanoTime();
        }
    }
    public static void rendered(Entity e,double x,double y,double z,float yaw,float partial) {
        var mc=Minecraft.getInstance();
        if(!collecting || studio.pose.model.ShaderPass.shadow() || mc.level==null || mc.player==null
            || e.isRemoved() || mc.level.getEntity(e.getId())!=e || e.isInvisibleTo(mc.player)) return;
        // EntityCulling cancels inside EntityRenderer, after dispatcher entry. Honor its public flag when installed.
        var culling=cullingMethods.computeIfAbsent(e.getClass(),type->{try {return Optional.of(type.getMethod("isCulled"));}catch(NoSuchMethodException ex) {return Optional.empty();}});
        try {if(culling.isPresent() && Boolean.TRUE.equals(culling.get().invoke(e))) return;} catch(ReflectiveOperationException ignored) {}
        var camera=mc.gameRenderer.getMainCamera().getPosition();
        building.put(e.getUUID(),new Entry(e.getUUID(),new ActorTransform(camera.x+x,camera.y+y,camera.z+z,yaw,
            net.minecraft.util.Mth.lerp(partial,e.xRotO,e.getXRot()))));
    }
    public static List<Entry> snapshot() {
        var mc=Minecraft.getInstance();
        if(level!=mc.level || System.nanoTime()-frame>3_000_000_000L) return null;
        var result=new LinkedHashMap<UUID,Entry>();
        // The photographer's own model is always part of the scene, including first person entry.
        if(mc.player!=null) result.put(mc.player.getUUID(),new Entry(mc.player.getUUID(),new ActorTransform(mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot())));
        for(var entry:completed) if(StudioState.INSTANCE.entity(entry.actor())!=null) result.put(entry.actor(),entry);
        return List.copyOf(result.values());
    }
}
