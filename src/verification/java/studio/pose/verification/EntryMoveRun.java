package studio.pose.verification;

import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import studio.pose.client.*;
import studio.pose.data.*;
import studio.pose.network.StudioNetwork;
import studio.pose.server.FreezeService;
import studio.pose.overlay.StudioOverlay;
import studio.pose.ui.StudioScreen;

/** Real main-frame entry collection, atomic server admission and mouse group translation. */
public final class EntryMoveRun {
    private int stage,ticks;
    private final long start=System.nanoTime();
    private final Path evidence=Path.of(System.getProperty("posestudio.acceptance.evidence"));
    private final List<String> checks=new ArrayList<>();
    private final List<UUID> mobs=new ArrayList<>();
    private UUID behind;
    private List<StudioNetwork.Entry> visible,group;
    private Vec3 camera;
    private float cameraYaw,cameraPitch;
    private StudioOverlay.Point point;
    private final Map<UUID,Map<String,BonePose>> poses=new HashMap<>();
    private int[] frozenTicks;
    private int captureChatDraws;
    @SubscribeEvent public void chat(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post e) {
        if(StudioState.INSTANCE.active && StudioState.INSTANCE.capture && e.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CHAT_PANEL.id())) captureChatDraws++;
    }
    private void check(boolean condition,String name) {if(!condition) throw new AssertionError(name);checks.add("PASS "+name);System.out.println("POSE_ENTRY "+checks.get(checks.size()-1));}
    private void next(int value) {stage=value;ticks=0;}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();var s=StudioState.INSTANCE;ticks++;
        try {
            if(System.nanoTime()-start>240_000_000_000L) throw new AssertionError("Entry/move timeout stage "+stage+" "+s.message);
            switch(stage) {
                case 0 -> {
                    if(mc.screen==null || mc.getOverlay()!=null || ticks<20) return;
                    Files.createDirectories(evidence);mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);
                    mc.createWorldOpenFlows().createFreshLevel("PoseEntryMove-"+System.currentTimeMillis(),new LevelSettings("PoseEntryMove",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());next(1);
                }
                case 1 -> {
                    if(mc.player==null || mc.screen!=null || ticks<20) return;
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.getSingleplayerServer().submit(()->{var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);var level=p.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);p.connection.teleport(0,-60,0,0,0);
                        var types=List.of(EntityType.WOLF,EntityType.HORSE,EntityType.CHICKEN,EntityType.RABBIT,EntityType.PIG,EntityType.COW,EntityType.SHEEP,EntityType.DONKEY,EntityType.LLAMA);
                        for(int i=0;i<types.size();i++) {var e=types.get(i).create(level);e.moveTo((i%5-2)*1.35,-60,6+i/5*3,180,0);e.setNoAi(true);level.addFreshEntity(e);mobs.add(e.getUUID());}
                        var e=EntityType.PIG.create(level);e.moveTo(0,-60,-5,0,0);e.setNoAi(true);level.addFreshEntity(e);behind=e.getUUID();
                    }).join();next(2);
                }
                case 2 -> {
                    if(ticks<40) return;visible=VisibleActors.snapshot();check(visible!=null && visible.size()>8,"Actual shader main-frame collector sees more than eight actors");
                    check(visible.stream().anyMatch(e->e.actor().equals(mc.player.getUUID())),"Own player included in first person entry");check(visible.stream().noneMatch(e->e.actor().equals(behind)),"Behind-camera entity excluded from startup freeze");
                    s.toggle();check(!s.active && !s.entering(),"Over-limit F6 refuses entry without opening editor");
                    mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);if(FreezeService.activeCount(p.serverLevel())!=0) throw new AssertionError("Partial freeze on limit rejection");for(int i=6;i<9;i++) p.serverLevel().getEntity(mobs.get(i)).discard();}).join();check(true,"Over-limit refusal leaves zero server locks");next(3);
                }
                case 3 -> {
                    if(ticks<40) return;visible=VisibleActors.snapshot();check(visible.size()==7,"Six visible mobs plus own player fit the default budget");camera=mc.gameRenderer.getMainCamera().getPosition();s.toggle();next(4);
                }
                case 4 -> {
                    if(!s.active || ticks<20) return;
                    check(visible.stream().allMatch(e->s.actors.containsKey(e.actor()) && s.actors.get(e.actor()).frozen),"Successful entry freezes every captured actor");
                    check(new Vec3(s.camera.x,s.camera.y,s.camera.z).distanceTo(camera)<.001,"F6 preserves the exact captured camera position");
                    frozenTicks=mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);if(FreezeService.frozen(p.serverLevel().getEntity(behind))) throw new AssertionError("Out-of-view entity frozen");return mobs.subList(0,6).stream().mapToInt(id->{var e=p.serverLevel().getEntity(id);if(!FreezeService.frozen(e)) throw new AssertionError("Visible actor not frozen");return e.tickCount;}).toArray();}).join();next(5);
                }
                case 5 -> {
                    if(ticks<25) return;mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);for(int i=0;i<6;i++) if(p.serverLevel().getEntity(mobs.get(i)).tickCount!=frozenTicks[i]) throw new AssertionError("Frozen mob kept ticking");}).join();check(true,"All visible mobs remain frozen together across ticks");
                    s.mode=StudioState.Mode.ACTOR;s.camera.x=0;s.camera.y=-57.8;s.camera.z=-7;s.camera.yaw=0;s.camera.pitch=8;s.camera.fov=65;
                    s.select(mc.player);s.select(s.entity(mobs.get(0)),true);s.select(s.entity(mobs.get(1)),true);group=s.selectedTransforms();check(group.size()==3,"Player and two distinct mob models selected as one group");
                    for(var entry:group) {var saved=new HashMap<String,BonePose>();s.actors.get(entry.actor()).bones.forEach((k,v)->saved.put(k,v.copy()));poses.put(entry.actor(),saved);}
                    mc.setScreen(new StudioScreen());next(6);
                }
                case 6 -> {
                    if(ticks<15) return;point=StudioOverlay.actorPoint(mobs.get(0));check(point!=null,"Actor model is projected for mouse picking");
                    camera=new Vec3(s.camera.x,s.camera.y,s.camera.z);cameraYaw=s.camera.yaw;cameraPitch=s.camera.pitch;
                    var screen=mc.screen;check(screen.mouseClicked(point.x(),point.y(),0) && StudioOverlay.actorDragging(),"Left mouse picks an already selected model for group translation");
                    screen.mouseDragged(point.x()+16,point.y()-9,0,16,-9);screen.mouseDragged(point.x()+28,point.y()-14,0,12,-5);screen.mouseReleased(point.x()+28,point.y()-14,0);next(7);
                }
                case 7 -> {
                    if(ticks<20) return;Vec3 delta=delta(s,group.get(0));check(delta.length()>.1,"Whole player actor moves through mouse drag");
                    for(var entry:group) {check(delta(s,entry).distanceTo(delta)<.0001,"Group member moves by the same XYZ delta: "+entry.actor());check(s.actors.get(entry.actor()).transform.yaw()==entry.transform().yaw(),"Translation preserves actor orientation");
                        var current=s.actors.get(entry.actor()).bones;for(var bone:poses.get(entry.actor()).entrySet()) check(Arrays.equals(bone.getValue().rotation,current.get(bone.getKey()).rotation) && Arrays.equals(bone.getValue().position,current.get(bone.getKey()).position),"Translation preserves local bone pose: "+bone.getKey());}
                    check(new Vec3(s.camera.x,s.camera.y,s.camera.z).equals(camera) && s.camera.yaw==cameraYaw && s.camera.pitch==cameraPitch,"Dragging models leaves camera unchanged");
                    screenshot(mc,"group-left-drag.png");s.undo.undo();next(8);
                }
                case 8 -> {
                    if(ticks<15) return;check(group.stream().allMatch(e->s.actors.get(e.actor()).transform.equals(e.transform())),"One undo restores all three actors after the entire mouse gesture");
                    point=StudioOverlay.actorPoint(mobs.get(0));mc.screen.mouseClicked(point.x(),point.y(),0);press(mc,"posestudio.ui.pose");check(!StudioOverlay.actorDragging(),"Changing to Pose mode releases Actor drag so controls cannot conflict");press(mc,"posestudio.ui.actor");
                    s.select(mc.player);point=StudioOverlay.actorPoint(mc.player.getUUID());check(mc.screen.mouseClicked(point.x(),point.y(),1) && StudioOverlay.actorDragging(),"Right mouse picks own player model for translation");mc.screen.mouseDragged(point.x()-15,point.y()+12,1,-15,12);mc.screen.mouseReleased(point.x()-15,point.y()+12,1);next(9);
                }
                case 9 -> {
                    if(ticks<15) return;check(delta(s,group.get(0)).length()>.1,"Right drag moves whole own player model");s.undo.undo();next(10);
                }
                case 10 -> {
                    if(ticks<15) return;check(s.actor().transform.equals(group.get(0).transform()),"Single actor right-drag undo restores player");
                    s.select(s.entity(mobs.get(0)),true);s.select(s.entity(mobs.get(1)),true);mc.setScreen(new StudioScreen());var fields=mc.screen.children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).toList();var t=s.actor().transform;
                    double[] values={t.x()+1,t.y()+.4,t.z()-2,t.yaw()+20,t.pitch()+5};for(int i=0;i<5;i++) fields.get(i).setValue(Double.toString(values[i]));press(mc,"posestudio.ui.apply");next(11);
                }
                case 11 -> {
                    if(ticks<15) return;for(var e:group) {var t=s.actors.get(e.actor()).transform;check(Math.abs(t.x()-e.transform().x()-1)<.001 && Math.abs(t.y()-e.transform().y()-.4)<.001 && Math.abs(t.z()-e.transform().z()+2)<.001 && t.yaw()==e.transform().yaw()+20,"Numeric group transform applies consistently");}s.undo.undo();next(12);
                }
                case 12 -> {
                    if(ticks<15) return;check(group.stream().allMatch(e->s.actors.get(e.actor()).transform.equals(e.transform())),"Numeric group undo restores all actors");
                    var t=group.get(0).transform();StudioNetwork.batch(false,List.of(new StudioNetwork.Entry(group.get(0).actor(),new ActorTransform(t.x()+2,t.y(),t.z(),t.yaw(),t.pitch())),new StudioNetwork.Entry(group.get(1).actor(),new ActorTransform(29999985,-60,0,0,0))));next(13);
                }
                case 13 -> {
                    if(ticks<15) return;mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);for(var e:group) if(!FreezeService.transform(p,e.actor()).equals(e.transform())) throw new AssertionError("Partial move on rejected batch");}).join();check(true,"Invalid group target is rejected atomically without moving other members");s.capture();captureChatDraws=0;next(131);
                }
                case 131 -> {if(ticks<10) return;check(captureChatDraws==0,"Capture suppresses native chat overlay including recent Studio errors");screenshot(mc,"capture-after-recent-error.png");s.exit(true);next(14);}
                case 14 -> {
                    if(ticks<20) return;mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);if(FreezeService.activeCount(p.serverLevel())!=0) throw new AssertionError("Exit left locks");String error=FreezeService.enter(p,List.of(visible.get(0),new StudioNetwork.Entry(UUID.randomUUID(),visible.get(0).transform())));if(error.isEmpty() || FreezeService.activeCount(p.serverLevel())!=0) throw new AssertionError("Partial entry after missing actor");}).join();check(true,"Exit restores scene and invalid startup batch leaves zero locks");Files.write(evidence.resolve("acceptance.txt"),checks);System.out.println("POSE_ENTRY COMPLETE");mc.stop();stage=99;
                }
            }
        } catch(Throwable error) {error.printStackTrace();try {Files.writeString(evidence.resolve("acceptance.txt"),String.join("\n",checks)+"\nFAIL stage "+stage+" "+error);}catch(Exception ignored){}s.exit(true);mc.stop();stage=99;}
    }
    private Vec3 delta(StudioState s,StudioNetwork.Entry entry) {var t=s.actors.get(entry.actor()).transform;var old=entry.transform();return new Vec3(t.x()-old.x(),t.y()-old.y(),t.z()-old.z());}
    private void press(Minecraft mc,String key) {String label=net.minecraft.client.resources.language.I18n.get(key);((Button)mc.screen.children().stream().filter(w->w instanceof Button b && b.getMessage().getString().equals(label)).findFirst().orElseThrow()).onPress();}
    private void screenshot(Minecraft mc,String name) throws Exception {try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(evidence.resolve(name));}}
}
