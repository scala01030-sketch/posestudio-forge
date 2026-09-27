package studio.pose.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import studio.pose.data.*;
import studio.pose.network.StudioNetwork;
import studio.pose.camera.CameraController;
import studio.pose.ui.StudioScreen;

public final class StudioState {
    public static final StudioState INSTANCE=new StudioState();
    public enum Mode { ACTOR, POSE, CAMERA }
    public final Map<UUID,ActorState> actors=new LinkedHashMap<>();
    public final CameraController camera=new CameraController();
    public final StudioUndo undo=new StudioUndo();
    public final Set<UUID> placed=new HashSet<>();
    private final Map<UUID,Long> retiring=new HashMap<>();
    private final Map<UUID,ActorTransform> pendingPlacements=new LinkedHashMap<>();
    public boolean guides;
    public int rotationAxis;
    public int translationAxis;
    public boolean actorRotate;
    public int actorRotationAxis;
    private Mode editMode=Mode.ACTOR;
    public boolean active, capture;
    private boolean requested, oldHideGui,awaitingEntryProps;
    private net.minecraft.client.CameraType oldCameraType;
    private net.minecraft.client.multiplayer.ClientLevel studioLevel;
    private int oldGuiScale;
    public Mode mode=Mode.ACTOR;
    public UUID selected;
    public final Set<UUID> selection=new LinkedHashSet<>();
    public String bone="head"; public Component message=Component.empty();
    private final UUID empty=new UUID(0,0);
    private final ActorTransform zero=new ActorTransform(0,0,0,0,0);
    public ActorState actor() { return actors.get(selected); }
    public Entity entity(UUID id) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null) return null;
        for(Entity e:mc.level.entitiesForRendering()) if(e.getUUID().equals(id)) return e;
        return null;
    }
    public void toggle() {
        if(active || requested) { exit(true); return; }
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.getConnection()==null) return;
        if(!StudioNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) { message=Component.translatable("posestudio.status.server_install"); mc.player.displayClientMessage(message,false); return; }
        var visible=VisibleActors.snapshot();
        long props=visible==null?0:visible.stream().filter(entry->studio.pose.StudioConfig.prop(entity(entry.actor()))).count();
        if(visible==null || visible.size()-props>studio.pose.StudioConfig.maxActors() || props>studio.pose.StudioConfig.maxProps()) {
            message=visible==null?Component.translatable("posestudio.status.wait_frame",ClientEvents.TOGGLE.getTranslatedKeyMessage()):Component.translatable("posestudio.error.entry_limit",visible.size()-props,studio.pose.StudioConfig.maxActors(),props,studio.pose.StudioConfig.maxProps());mc.player.displayClientMessage(message,false);return;
        }
        requested=true;studioLevel=mc.level;camera.begin(mc.gameRenderer.getMainCamera());
        for(var entry:visible) {var a=new ActorState(entry.actor(),entry.transform());a.frozen=true;actors.put(a.id,a);pin(a);}
        StudioNetwork.batch(true,visible);
    }
    public boolean entering() {return requested && !active;}
    public void sceneProps(java.util.List<StudioNetwork.Entry> entries) {
        if(!active) return;var live=new HashSet<UUID>();for(var entry:entries) live.add(entry.actor());
        for(UUID id:new HashSet<>(placed)) if(!live.contains(id)) {
            retiring.put(id,System.nanoTime()+10_000_000_000L);placed.remove(id);actors.remove(id);pendingPlacements.remove(id);selection.remove(id);
            if(id.equals(selected)) selected=selection.stream().findFirst().orElse(null);
        }
        for(var entry:entries) {
            placed.add(entry.actor());var actor=actors.computeIfAbsent(entry.actor(),id->new ActorState(id,entry.transform()));
            actor.frozen=true;actor.transform=entry.transform();pin(actor);
        }
        if(awaitingEntryProps) {awaitingEntryProps=false;message=Component.translatable("posestudio.status.entry_frozen",actors.values().stream().filter(a->a.frozen).count());}
        if(Minecraft.getInstance().screen instanceof StudioScreen screen) screen.refresh();
    }
    public void batchReply(StudioNetwork.BatchReply r) {
        var mc=Minecraft.getInstance();
        if(!r.error().isEmpty()) {
            if(r.enter()) exit(false);
            message=Component.translatable(r.error());if(mc.player!=null) mc.player.displayClientMessage(message,false);return;
        }
        if(r.enter()) {
            if(!requested) {StudioNetwork.send(StudioNetwork.END,empty,zero);return;}
            awaitingEntryProps=true;
            // Camera was captured on F6. Retain it while the authoritative freeze acknowledgement arrives.
            reply(new StudioNetwork.Reply(StudioNetwork.BEGIN,empty,zero,""));
        }
        if(!active) return;
        for(var entry:r.actors()) {var a=actors.computeIfAbsent(entry.actor(),id->new ActorState(id,entry.transform()));a.frozen=true;a.transform=entry.transform();a.renderRequestedNanos=System.nanoTime();pin(a);}
        if(r.enter()) {if(mc.player!=null) select(mc.player);message=Component.translatable("posestudio.status.entry_frozen",r.actors().size());}
        if(mc.screen instanceof StudioScreen screen) screen.refresh();
    }
    public void reply(StudioNetwork.Reply r) {
        if(!r.error().isEmpty()) {
            message=r.error().startsWith("posestudio.")?Component.translatable(r.error()):Component.literal(r.error());
            if(r.action()==StudioNetwork.BEGIN) requested=false;
            if(Minecraft.getInstance().player!=null) Minecraft.getInstance().player.displayClientMessage(message,false);
            return;
        }
        if(r.action()==StudioNetwork.BEGIN && requested) {
            Minecraft mc=Minecraft.getInstance(); active=true; studioLevel=mc.level; oldHideGui=mc.options.hideGui; oldCameraType=mc.options.getCameraType();oldGuiScale=mc.options.guiScale().get();
            if(mc.getWindow().getGuiScaledWidth()<640 || mc.getWindow().getGuiScaledHeight()<360) {
                int scale=Math.max(1,Math.min(mc.getWindow().getWidth()/640,mc.getWindow().getHeight()/360));
                mc.options.guiScale().set(scale);mc.resizeDisplay();
            }
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.options.hideGui=true;
            mc.setScreen(new StudioScreen()); message=Component.translatable("posestudio.status.select_actor");
        }
        if(!active) return;
        if(r.action()==StudioNetwork.PLACE) { pendingPlacements.put(r.actor(),r.transform());placed.add(r.actor());message=Component.translatable("posestudio.status.placing");return; }
        if(r.action()==StudioNetwork.REMOVE) { retiring.put(r.actor(),System.nanoTime()+10_000_000_000L);actors.remove(r.actor());placed.remove(r.actor());selection.remove(r.actor());if(r.actor().equals(selected)) selected=selection.stream().findFirst().orElse(null);undo.clear();if(Minecraft.getInstance().screen instanceof StudioScreen screen) screen.refresh();return; }
        ActorState a=actors.get(r.actor());
        if(a==null) return;
        if(r.action()==StudioNetwork.FREEZE) { a.frozen=true;a.renderRequestedNanos=System.nanoTime();a.lastMainRenderNanos=0;a.transform=r.transform();pin(a);message=Component.translatable("posestudio.status.frozen"); }
        if(r.action()==StudioNetwork.RELEASE) { undo.clear();a.frozen=false; a.bones.clear();a.boneScales.clear(); a.nodes.clear(); message=Component.translatable("posestudio.status.restored"); }
        if(r.action()==StudioNetwork.MOVE) { a.transform=r.transform(); pin(a); }
        if(Minecraft.getInstance().screen instanceof StudioScreen screen) screen.refresh();
    }
    public void select(Entity e) {
        if(e==null) return;
        selected=e.getUUID(); actors.computeIfAbsent(selected,id->new ActorState(id,new ActorTransform(e.getX(),e.getY(),e.getZ(),e.getYRot(),e.getXRot())));
        selection.clear();selection.add(selected);
        // Model discovery is synchronous and must not depend on visibility or a shader render pass.
        var adapter=studio.pose.model.RenderContext.inspect(e,actor());
        if(!actor().bones.containsKey(bone) || (!guides && !adapter.player && !actor().editableBones.contains(bone))) {
            bone=adapter.player?"head":actor().editableBones.stream().sorted(java.util.Comparator.comparingInt((String name)->name.toLowerCase(java.util.Locale.ROOT).contains("head")?0:1).thenComparing(name->name)).findFirst().orElse("head");
        }
        if(Minecraft.getInstance().screen instanceof StudioScreen s) s.refresh();
    }
    public void select(Entity e,boolean additive) {
        if(!additive) {select(e);return;}
        if(e==null) return;
        var previous=new LinkedHashSet<>(selection);boolean remove=previous.remove(e.getUUID());
        select(e);if(!remove) previous.add(e.getUUID());selection.clear();selection.addAll(previous);
        if(remove) {selected=selection.stream().reduce((a,b)->b).orElse(null);if(Minecraft.getInstance().screen instanceof StudioScreen screen) screen.refresh();}
    }
    public java.util.List<StudioNetwork.Entry> selectedTransforms() {
        var result=new ArrayList<StudioNetwork.Entry>();
        for(UUID id:selection) {var a=actors.get(id);if(a!=null && a.frozen) result.add(new StudioNetwork.Entry(id,a.transform));}
        return result;
    }
    public boolean movableSelection() {return !selection.isEmpty() && selectedTransforms().size()==selection.size();}
    public void moveSelection(java.util.List<StudioNetwork.Entry> origin,net.minecraft.world.phys.Vec3 delta) {
        var moved=new ArrayList<StudioNetwork.Entry>();
        for(var entry:origin) {var t=entry.transform();var next=new ActorTransform(t.x()+delta.x,t.y()+delta.y,t.z()+delta.z,t.yaw(),t.pitch(),t.roll());if(!next.valid()) return;moved.add(new StudioNetwork.Entry(entry.actor(),next));}
        StudioNetwork.batch(false,moved);
    }
    public void freeze() {
        ActorState a=actor(); if(a==null) return;
        if(placed.contains(a.id)) { message=Component.translatable("posestudio.status.placed_frozen");return; }
        StudioNetwork.send(a.frozen?StudioNetwork.RELEASE:StudioNetwork.FREEZE,a.id,a.transform);
    }
    public void move(ActorTransform t) {
        ActorState a=actor(); if(a==null || !a.frozen) { message=Component.translatable("posestudio.status.freeze_first"); return; }
        if(!t.valid()) { message=Component.translatable("posestudio.status.invalid_transform"); return; }
        StudioNetwork.send(StudioNetwork.MOVE,a.id,t);
    }
    private void pin(ActorState a) {
        Entity e=entity(a.id); if(e==null) return;
        ActorTransform t=a.transform;
        e.setPos(t.x(),t.y(),t.z()); e.setYRot(t.yaw()); e.setXRot(t.pitch());
        e.xo=e.xOld=t.x(); e.yo=e.yOld=t.y(); e.zo=e.zOld=t.z(); e.yRotO=t.yaw(); e.xRotO=t.pitch(); e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if(e instanceof LivingEntity l) { l.yBodyRot=l.yBodyRotO=t.yaw(); l.yHeadRot=l.yHeadRotO=t.yaw(); l.attackAnim=l.oAttackAnim=0; l.walkAnimation.setSpeed(0); }
    }
    public void tick() {
        if(Minecraft.getInstance().level!=studioLevel) { exit(true); return; }
        var iterator=pendingPlacements.entrySet().iterator();
        while(iterator.hasNext()) { var entry=iterator.next();Entity e=entity(entry.getKey());if(e==null) continue;select(e);ActorState a=actor();a.transform=entry.getValue();a.frozen=true;a.renderRequestedNanos=System.nanoTime();iterator.remove();message=Component.translatable("posestudio.status.placed");if(Minecraft.getInstance().screen instanceof StudioScreen screen) screen.refresh(); }
        for(ActorState a:actors.values()) if(a.frozen) pin(a);
    }
    public ActorTransform placementInView() {
        double yaw=Math.toRadians(camera.yaw),pitch=Math.toRadians(camera.pitch),distance=4;
        return new ActorTransform(camera.x-Math.sin(yaw)*Math.cos(pitch)*distance,
            camera.y-Math.sin(pitch)*distance-1,camera.z+Math.cos(yaw)*Math.cos(pitch)*distance,camera.yaw+180,0);
    }
    public void removePlaced() {
        ActorState a=actor();if(a==null || !placed.contains(a.id)) {message=Component.translatable("posestudio.error.not_placed");return;}
        StudioNetwork.send(StudioNetwork.REMOVE,a.id,a.transform);
    }
    public void capture() {
        if(!active) return;
        studio.pose.overlay.StudioOverlay.release();
        if(capture) {openEditor();return;}
        if(mode!=Mode.CAMERA) editMode=mode;
        capture=true;Minecraft.getInstance().setScreen(null);
    }
    public void setMode(Mode next) {studio.pose.overlay.StudioOverlay.release();if(mode!=Mode.CAMERA) editMode=mode;mode=next;if(next!=Mode.CAMERA) editMode=next;}
    public void fly() {if(!active) return;setMode(Mode.CAMERA);capture=false;Minecraft.getInstance().setScreen(null);}
    public void openEditor() {if(!active) return;studio.pose.overlay.StudioOverlay.release();capture=false;mode=editMode;Minecraft.getInstance().setScreen(new StudioScreen());}
    public void editor() {if(active) {if(Minecraft.getInstance().screen instanceof StudioScreen) fly();else openEditor();}}
    public boolean suppressesPhysics(UUID id) {
        retiring.values().removeIf(deadline->deadline<System.nanoTime());
        ActorState actor=actors.get(id);return retiring.containsKey(id) || placed.contains(id) || (active && actor!=null && actor.frozen);
    }
    public void exit(boolean send) {
        Minecraft mc=Minecraft.getInstance();
        if(send && (requested || active) && mc.getConnection()!=null) StudioNetwork.send(StudioNetwork.END,empty,zero);
        requested=false;awaitingEntryProps=false;
        if(active) {
            mc.options.hideGui=oldHideGui;if(oldCameraType!=null) mc.options.setCameraType(oldCameraType);
            mc.options.guiScale().set(oldGuiScale);mc.resizeDisplay();
        }
        if(mc.screen instanceof StudioScreen) mc.setScreen(null);
        for(UUID id:placed) retiring.put(id,System.nanoTime()+10_000_000_000L);
        active=false; capture=false;mode=editMode=Mode.ACTOR; actors.clear();placed.clear();pendingPlacements.clear(); selected=null;selection.clear(); studioLevel=null; camera.end();
        undo.clear();studio.pose.overlay.StudioOverlay.release();
        studio.pose.model.RenderContext.clear();
        studio.pose.model.PoseController.clear();
    }
}
