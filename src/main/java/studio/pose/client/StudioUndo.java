package studio.pose.client;

import java.util.LinkedHashMap;
import java.util.Map;
import studio.pose.data.BonePose;
import net.minecraft.network.chat.Component;

/** Ten completed adjustments. A mouse gesture is remembered only at its first change. */
public final class StudioUndo {
    public static final int LIMIT=10;
    private final java.util.Deque<Runnable> history=new java.util.ArrayDeque<>();
    public boolean available() { return !history.isEmpty(); }
    public int size() { return history.size(); }
    public void remember(Runnable action) { history.addFirst(java.util.Objects.requireNonNull(action));if(history.size()>LIMIT) history.removeLast(); }
    public void clear() { history.clear(); }
    public void undo() {
        if(history.isEmpty()) { StudioState.INSTANCE.message=Component.translatable("posestudio.status.no_undo");return; }
        history.removeFirst().run();
        StudioState.INSTANCE.message=Component.translatable("posestudio.status.undone",history.size());
    }
    public Runnable poseSnapshot(ActorState actor) {
        Map<String,BonePose> saved=new LinkedHashMap<>();actor.bones.forEach((k,v)->saved.put(k,v.copy()));
        return ()->{if(!actor.frozen) return;actor.bones.clear();saved.forEach((k,v)->actor.bones.put(k,v.copy()));};
    }
    public Runnable actorSnapshot(ActorState actor) {
        var transform=actor.transform;
        return ()->{if(actor.frozen) studio.pose.network.StudioNetwork.send(studio.pose.network.StudioNetwork.MOVE,actor.id,transform);};
    }
    public Runnable cameraSnapshot() {
        var c=StudioState.INSTANCE.camera;double x=c.x,y=c.y,z=c.z,fov=c.fov,speed=c.speed;float yaw=c.yaw,pitch=c.pitch,roll=c.roll;
        return ()->{c.x=x;c.y=y;c.z=z;c.fov=fov;c.speed=speed;c.yaw=yaw;c.pitch=pitch;c.roll=roll;};
    }
    public Runnable actorsSnapshot(java.util.List<studio.pose.network.StudioNetwork.Entry> entries) {
        var saved=java.util.List.copyOf(entries);
        return ()->{var s=StudioState.INSTANCE;if(s.active && saved.stream().allMatch(e->s.actors.containsKey(e.actor()) && s.actors.get(e.actor()).frozen)) studio.pose.network.StudioNetwork.batch(false,saved);};
    }
}
