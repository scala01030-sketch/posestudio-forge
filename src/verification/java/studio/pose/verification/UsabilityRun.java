package studio.pose.verification;

import java.nio.file.*;
import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import studio.pose.client.*;
import studio.pose.data.*;
import studio.pose.network.StudioNetwork;
import studio.pose.overlay.StudioOverlay;
import studio.pose.ui.StudioScreen;
import studio.pose.verification.mixin.KeyboardAccess;

/** Exercises native keyboard dispatch, GUI gestures and integrated-server axis movement in the user's pack. */
public final class UsabilityRun {
    private int stage,ticks,viewIndex,axisIndex;
    private final long start=System.nanoTime();
    private final Path evidence=Path.of(System.getProperty("posestudio.acceptance.evidence"));
    private final List<String> checks=new ArrayList<>();
    private final List<UUID> models=new ArrayList<>();
    private List<StudioNetwork.Entry> group;
    private List<StudioNetwork.Entry> preserved;
    private StudioOverlay.Point handle;
    private InputConstants.Key editorKey,captureKey,toggleKey;
    private long captureGuideFrame;
    private Vec3 camera;
    private double fov,elbow;
    private float yaw,pitch,roll;
    private int rotationIndex;
    private ActorTransform rotationBefore;
    private org.joml.Matrix4f headMatrix;
    private final List<UUID> props=new ArrayList<>();
    private void check(boolean condition,String name) {if(!condition) throw new AssertionError(name);checks.add("PASS "+name);System.out.println("POSE_USABILITY "+checks.get(checks.size()-1));}
    private void next(int value) {stage=value;ticks=0;}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();var s=StudioState.INSTANCE;ticks++;
        try {
            if(System.nanoTime()-start>240_000_000_000L) throw new AssertionError("Usability timeout stage "+stage+" "+s.message);
            switch(stage) {
                case 0 -> {
                    if(mc.screen==null || mc.getOverlay()!=null || ticks<20) return;
                    Files.createDirectories(evidence);mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);
                    mc.createWorldOpenFlows().createFreshLevel("PoseUsability-"+System.currentTimeMillis(),new LevelSettings("PoseUsability",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(43,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());next(1);
                }
                case 1 -> {
                    if(mc.player==null || mc.screen!=null || ticks<20) return;
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.getSingleplayerServer().submit(()->{var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);var level=p.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);p.connection.teleport(0,-60,0,0,0);
                        for(String type:List.of("minecraft:horse","minecraft:cow","tacz:target_minecart")) {var e=BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(type)).create(level);e.moveTo((models.size()-1)*2,-60,6,180,0);if(e instanceof Mob mob) mob.setNoAi(true);level.addFreshEntity(e);models.add(e.getUUID());}
                    }).join();next(2);
                }
                case 2 -> {
                    if(ticks<40) return;nativeKey(mc,ClientEvents.TOGGLE.getKey().getValue());next(3);
                }
                case 3 -> {
                    if(!s.active || ticks<20) return;
                    check(mc.screen instanceof StudioScreen,"Actual bound start key opens Studio without a conflicting Physics screen");
                    check(models.stream().allMatch(id->s.actors.containsKey(id) && s.actors.get(id).frozen),"Horse, cow and TaCZ actor frozen on native entry");
                    s.select(mc.player);s.setMode(StudioState.Mode.POSE);s.bone="left_elbow";mc.setScreen(new StudioScreen());applyRotation(mc,65);elbow=s.actor().bones.get(s.bone).rotation[0];
                    preserved=s.selectedTransforms();s.camera.x=0;s.camera.y=-58.8;s.camera.z=-6;s.camera.yaw=0;s.camera.pitch=3;s.camera.roll=12;s.camera.fov=61;saveCamera(s);
                    press(mc,"posestudio.ui.camera");press(mc,"posestudio.ui.fly");check(mc.screen==null && s.active && s.mode==StudioState.Mode.CAMERA,"Camera Fly closes only the panel and retains the Studio session");next(4);
                }
                case 4 -> {
                    if(ticks<15) return;check(System.nanoTime()-studio.pose.overlay.StudioHud.lastFrameNanos()<1_000_000_000L,"Free camera return guide renders while normal HUD is hidden");screenshot(mc,"free-camera-return-hint.png");
                    nativeKey(mc,ClientEvents.EDITOR.getKey().getValue());assertReturned(mc,s,"Bound editor key");repeatKey(mc,ClientEvents.EDITOR.getKey().getValue());check(mc.screen instanceof StudioScreen,"Holding the editor key does not bounce between views");nativeKey(mc,ClientEvents.EDITOR.getKey().getValue());check(mc.screen==null,"Same editor key re-enters free camera");next(5);
                }
                case 5 -> {
                    if(ticks<10) return;nativeKey(mc,GLFW.GLFW_KEY_ESCAPE);assertReturned(mc,s,"Escape fallback");
                    nativeKey(mc,ClientEvents.CAPTURE.getKey().getValue());captureGuideFrame=studio.pose.overlay.StudioHud.lastFrameNanos();check(s.capture && mc.screen==null,"Bound capture key hides all editor UI");next(6);
                }
                case 6 -> {
                    if(ticks<10) return;check(captureGuideFrame==studio.pose.overlay.StudioHud.lastFrameNanos(),"Capture never draws the free-camera return hint");screenshot(mc,"capture-preserved-scene.png");nativeKey(mc,ClientEvents.CAPTURE.getKey().getValue());assertReturned(mc,s,"Capture return");
                    editorKey=ClientEvents.EDITOR.getKey();ClientEvents.EDITOR.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F10));KeyMapping.resetMapping();
                    check(StudioKeys.shortcuts(false).getString().contains(ClientEvents.EDITOR.getTranslatedKeyMessage().getString()),"Editor footer reflects a live F10 rebind");
                    check(StudioKeys.returnHint().getString().contains(ClientEvents.EDITOR.getTranslatedKeyMessage().getString()),"Camera hint reflects a live F10 rebind");
                    nativeKey(mc,GLFW.GLFW_KEY_F10);check(mc.screen==null,"Remapped F10 enters free camera immediately");next(7);
                }
                case 7 -> {
                    if(ticks<10) return;nativeKey(mc,GLFW.GLFW_KEY_F10);assertReturned(mc,s,"Remapped F10 return");
                    ClientEvents.EDITOR.setKey(editorKey);KeyMapping.resetMapping();editorKey=null;
                    captureKey=ClientEvents.CAPTURE.getKey();toggleKey=ClientEvents.TOGGLE.getKey();ClientEvents.CAPTURE.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F11));ClientEvents.TOGGLE.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F12));KeyMapping.resetMapping();
                    check(StudioKeys.shortcuts(true).getString().contains(ClientEvents.CAPTURE.getTranslatedKeyMessage().getString()) && StudioKeys.shortcuts(true).getString().contains(ClientEvents.TOGGLE.getTranslatedKeyMessage().getString()),"Actor footer reflects live capture and start-key rebinds");
                    check(StudioKeys.returnHint().getString().contains(ClientEvents.CAPTURE.getTranslatedKeyMessage().getString()) && StudioKeys.returnHint().getString().contains(ClientEvents.TOGGLE.getTranslatedKeyMessage().getString()),"Camera hint reflects live capture and start-key rebinds");
                    nativeKey(mc,GLFW.GLFW_KEY_F11);check(s.capture && mc.screen==null,"Remapped capture key performs Capture instead of fullscreen");nativeKey(mc,GLFW.GLFW_KEY_F11);assertReturned(mc,s,"Remapped Capture return");
                    ClientEvents.CAPTURE.setKey(captureKey);ClientEvents.TOGGLE.setKey(toggleKey);KeyMapping.resetMapping();captureKey=toggleKey=null;
                    s.undo.clear();next(8);
                }
                case 8 -> {
                    if(ticks<10) return;
                    if(viewIndex==4) {s.setMode(StudioState.Mode.ACTOR);s.select(s.entity(models.get(0)),true);s.select(s.entity(models.get(2)),true);s.select(mc.player,true);s.select(mc.player,true);check(s.selection.size()==3,"Player, horse and TaCZ remain selected as a group");s.selected=mc.player.getUUID();group=s.selectedTransforms();mc.setScreen(new StudioScreen());next(10);return;}
                    press(mc,"posestudio.ui.view_"+new String[]{"front","back","left","right"}[viewIndex]);next(9);
                }
                case 9 -> {
                    if(ticks<12) return;
                    float expected=s.actor().transform.yaw()+new int[]{180,0,90,-90}[viewIndex];check(s.camera.yaw==expected && s.camera.pitch==0 && s.camera.roll==0,"Standard view has correct relative facing and neutral roll: "+viewIndex);
                    Vec3 center=mc.player.getBoundingBox().getCenter();double radians=Math.toRadians(s.camera.yaw);Vec3 direction=new Vec3(-Math.sin(radians),0,Math.cos(radians));check(center.subtract(new Vec3(s.camera.x,s.camera.y,s.camera.z)).normalize().distanceTo(direction)<.0001,"Standard view looks directly at the selected actor: "+viewIndex);
                    check(s.actor().transform.equals(preserved.get(0).transform()) && s.actor().bones.get("left_elbow").rotation[0]==elbow,"Standard view preserves actor placement and elbow pose: "+viewIndex);
                    screenshot(mc,"view-"+new String[]{"front","back","left","right"}[viewIndex]+".png");viewIndex++;next(8);
                }
                case 10 -> {
                    if(ticks<12) return;if(axisIndex==3) {s.select(mc.player);s.setMode(StudioState.Mode.POSE);s.bone="head";mc.setScreen(new StudioScreen());s.undo.clear();next(14);return;}
                    s.translationAxis=axisIndex;studio.pose.camera.StudioViews.focus(0);s.undo.clear();next(11);
                }
                case 11 -> {
                    if(ticks<12) return;handle=StudioOverlay.translationHandle(axisIndex,.85);check(handle!=null,"Projected world axis handle is visible: "+axisIndex);
                    saveCamera(s);check(mc.screen.mouseClicked(handle.x(),handle.y(),0) && StudioOverlay.actorDragging() && s.translationAxis==axisIndex,"Mouse selects the requested world axis: "+axisIndex);
                    double dx=axisIndex==0?-30:0,dy=axisIndex==0?0:-30;
                    mc.screen.mouseDragged(handle.x()+dx*.5,handle.y()+dy*.5,0,dx*.5,dy*.5);mc.screen.mouseDragged(handle.x()+dx,handle.y()+dy,0,dx*.5,dy*.5);mc.screen.mouseReleased(handle.x()+dx,handle.y()+dy,0);next(12);
                }
                case 12 -> {
                    if(ticks<18) return;Vec3 first=null;
                    for(var entry:group) {var t=s.actors.get(entry.actor()).transform;var old=entry.transform();Vec3 delta=new Vec3(t.x()-old.x(),t.y()-old.y(),t.z()-old.z());double[] components={delta.x,delta.y,delta.z};check(Math.abs(components[axisIndex])>.01,"Selected axis actually moves the entire actor: "+axisIndex+" "+entry.actor());for(int axis=0;axis<3;axis++) if(axis!=axisIndex) check(Math.abs(components[axis])<.0001,"Unselected axis stays fixed: "+axis+" "+entry.actor());if(first==null) first=delta;else check(delta.distanceTo(first)<.0001,"Group shares exactly the same axis displacement");}
                    check(s.undo.size()==1,"Two drag events produce only one undo entry");checkCamera(s,"Axis movement preserves the free camera");
                    mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);for(var entry:group) if(!studio.pose.server.FreezeService.transform(p,entry.actor()).equals(s.actors.get(entry.actor()).transform)) throw new AssertionError("Axis move not applied on server");}).join();check(true,"Axis move acknowledged by the integrated server");
                    screenshot(mc,"axis-"+"xyz".charAt(axisIndex)+"-group.png");s.undo.undo();next(13);
                }
                case 13 -> {
                    if(ticks<15) return;check(group.stream().allMatch(e->s.actors.get(e.actor()).transform.equals(e.transform())),"One undo restores the complete group on axis "+axisIndex);axisIndex++;next(10);
                }
                case 14 -> {
                    if(ticks<10) return;for(int i=1;i<=12;i++) applyRotation(mc,i);check(s.undo.size()==10,"Twelve numeric edits retain exactly the most recent ten adjustments");
                    for(int i=11;i>=2;i--) {s.undo.undo();check(s.actor().bones.get("head").rotation[0]==i,"Undo restores numeric edit "+i+" in reverse order");}
                    check(!s.undo.available() && s.undo.size()==0,"Ten undos exhaust history and discard the two oldest edits");s.undo.undo();check(s.actor().bones.get("head").rotation[0]==2,"An eleventh undo leaves the model unchanged");
                    applyRotation(mc,23);check(s.undo.size()==1,"A new adjustment after undo creates a fresh entry");s.undo.undo();check(s.actor().bones.get("head").rotation[0]==2,"New adjustment undo returns to the current baseline");
                    s.capture();nativeKey(mc,GLFW.GLFW_KEY_ESCAPE);check(mc.screen instanceof StudioScreen && !s.capture && s.active,"Escape returns from Capture without releasing actors");
                    if(Boolean.getBoolean("posestudio.acceptance.final")) {s.setMode(StudioState.Mode.ACTOR);s.actorRotate=true;rotationBefore=s.actor().transform;headMatrix=new org.joml.Matrix4f(s.actor().nodes.get("head").matrix());mc.setScreen(new StudioScreen());var fields=mc.screen.children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).toList();fields.get(3).setValue("35");fields.get(4).setValue("120");fields.get(5).setValue("40");press(mc,"posestudio.ui.apply");next(20);}else {nativeKey(mc,ClientEvents.TOGGLE.getKey().getValue());next(15);}
                }
                case 20 -> {
                    if(ticks==1) {WireChecks.verify();check(true,"Forty-entry codec preserves full rotation and rejects forty-one entries");}
                    if(ticks<20) return;check(s.actor().transform.yaw()==35 && s.actor().transform.pitch()==120 && s.actor().transform.roll()==40,"Whole actor accepts free yaw/pitch/roll including tilt beyond 90 degrees");
                    check(!s.actor().nodes.get("head").matrix().equals(headMatrix,.001f),"Whole actor rotation reaches the actual rendered model matrix");check(s.actor().bones.get("head").rotation[0]==2,"Whole rotation preserves local head pose");screenshot(mc,"actor-free-rotation.png");s.undo.undo();next(21);
                }
                case 21 -> {if(ticks<15) return;check(s.actor().transform.equals(rotationBefore),"Whole actor rotation undo restores yaw/pitch/roll");s.actorRotationAxis=rotationIndex;mc.setScreen(new StudioScreen());next(22);}
                case 22 -> {if(ticks<12) return;handle=StudioOverlay.actorRotationHandle(rotationIndex,.7);check(handle!=null && mc.screen.mouseClicked(handle.x(),handle.y(),0) && StudioOverlay.actorDragging(),"Whole actor rotation ring picks axis "+rotationIndex);mc.screen.mouseDragged(handle.x()+18,handle.y()-14,0,18,-14);mc.screen.mouseDragged(handle.x()+24,handle.y()-19,0,6,-5);mc.screen.mouseReleased(handle.x()+24,handle.y()-19,0);next(23);}
                case 23 -> {if(ticks<15) return;var t=s.actor().transform;float[] before={rotationBefore.pitch(),rotationBefore.yaw(),rotationBefore.roll()},after={t.pitch(),t.yaw(),t.roll()};check(Math.abs(after[rotationIndex]-before[rotationIndex])>.1,"Whole actor ring actually rotates axis "+rotationIndex);for(int i=0;i<3;i++) if(i!=rotationIndex) check(after[i]==before[i],"Whole rotation keeps the other angle fixed: "+i);check(t.x()==rotationBefore.x() && t.y()==rotationBefore.y() && t.z()==rotationBefore.z(),"Rotation does not translate actor");s.undo.undo();if(++rotationIndex<3) next(21);else next(24);}
                case 24 -> {
                    if(ticks<15) return;s.actorRotate=false;s.exit(true);mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=p.serverLevel();var all=new ArrayList<Entity>();level.getAllEntities().forEach(all::add);for(Entity e:all) if(!(e instanceof net.minecraft.world.entity.player.Player)) e.discard();for(int i=0;i<30;i++) {var item=new net.minecraft.world.entity.item.ItemEntity(level,4+(i%5)*2,-60+(i/5)*.7,6,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(item);props.add(item.getUUID());}}).join();next(25);
                }
                case 25 -> {
                    if(ticks<30) return;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(-5);mc.player.xRotO=-5;
                    mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.connection.teleport(8,-60,-15,0,-5);}).join();next(26);
                }
                case 26 -> {
                    if(ticks<30) return;var visible=VisibleActors.snapshot();check(visible.stream().filter(e->studio.pose.StudioConfig.prop(s.entity(e.actor()))).count()==30,"Actual frame sees all thirty distinct dropped-item entities");check(studio.pose.StudioConfig.maxProps()==25,"Default independent prop budget is twenty-five");s.toggle();check(!s.active && !s.entering(),"Thirty visible props reject default twenty-five budget without partial entry");
                    mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var entries=new ArrayList<StudioNetwork.Entry>();for(UUID id:props) {var e=p.serverLevel().getEntity(id);entries.add(new StudioNetwork.Entry(id,new ActorTransform(e.getX(),e.getY(),e.getZ(),0,0,0)));}for(int limit:new int[]{20,25,30}) {studio.pose.StudioConfig.MAX_PROPS.set(limit);var subset=new ArrayList<>(entries.subList(0,limit));subset.add(new StudioNetwork.Entry(p.getUUID(),new ActorTransform(p.getX(),p.getY(),p.getZ(),0,0)));if(!studio.pose.server.FreezeService.enter(p,subset).isEmpty() || studio.pose.server.FreezeService.propCount(p.serverLevel())!=limit) throw new AssertionError("Prop admission limit "+limit);if(limit<30 && !studio.pose.server.FreezeService.acquire(p,props.get(limit)).equals("posestudio.error.placement_limit")) throw new AssertionError("Prop overflow accepted");if(!studio.pose.server.FreezeService.moveBatch(p,subset).isEmpty()) throw new AssertionError("Large batch rejected");studio.pose.server.FreezeService.end(p.getUUID());}}).join();check(true,"Server independently enforces twenty, twenty-five and thirty props beside a player");s.toggle();next(27);
                }
                case 27 -> {
                    if(!s.active || ticks<25) return;check(s.actors.size()==31 && s.actors.values().stream().allMatch(a->a.frozen),"Thirty props and player enter atomically through the real network");s.camera.x=8;s.camera.y=-58;s.camera.z=-15;s.camera.yaw=0;s.camera.pitch=0;s.camera.roll=0;s.camera.fov=70;
                    var entries=new ArrayList<StudioNetwork.Entry>();for(var a:s.actors.values()) {var t=a.transform;entries.add(new StudioNetwork.Entry(a.id,new ActorTransform(t.x(),t.y()+.25,t.z(),20,30,40)));}group=s.actors.values().stream().map(a->new StudioNetwork.Entry(a.id,a.transform)).toList();s.undo.remember(s.undo.actorsSnapshot(group));StudioNetwork.batch(false,entries);next(28);
                }
                case 28 -> {
                    if(ticks<20) return;check(s.actors.values().stream().allMatch(a->a.transform.roll()==40 && a.transform.pitch()==30 && a.transform.yaw()==20),"Thirty-one actor batch retains all free rotation angles over the wire");s.undo.undo();next(29);
                }
                case 29 -> {
                    if(ticks<20) return;check(group.stream().allMatch(e->s.actors.get(e.actor()).transform.equals(e.transform())),"One undo restores all thirty-one actors");s.capture();next(30);
                }
                case 30 -> {
                    if(ticks<20) return;check(s.actors.values().stream().allMatch(a->a.lastMainRenderNanos>0),"Thirty props and player have actual main-frame render records");screenshot(mc,"thirty-props-capture.png");s.exit(true);studio.pose.StudioConfig.MAX_PROPS.set(25);next(15);
                }
                case 15 -> {
                    if(ticks<15) return;check(!s.active && !s.undo.available(),"Exit clears the ten-step history and closes Studio");mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);if(studio.pose.server.FreezeService.activeCount(p.serverLevel())!=0) throw new AssertionError("Exit left locks");}).join();check(true,"Exit releases all frozen scene actors");Files.write(evidence.resolve("acceptance.txt"),checks);System.out.println("POSE_USABILITY COMPLETE");mc.stop();stage=99;
                }
            }
        } catch(Throwable error) {error.printStackTrace();if(editorKey!=null) ClientEvents.EDITOR.setKey(editorKey);if(captureKey!=null) ClientEvents.CAPTURE.setKey(captureKey);if(toggleKey!=null) ClientEvents.TOGGLE.setKey(toggleKey);KeyMapping.resetMapping();try {Files.writeString(evidence.resolve("acceptance.txt"),String.join("\n",checks)+"\nFAIL stage "+stage+" "+error);}catch(Exception ignored){}s.exit(true);mc.stop();stage=99;}
    }
    private void nativeKey(Minecraft mc,int key) {var keyboard=(KeyboardAccess)(Object)mc.keyboardHandler;keyboard.posefixtures$press(mc.getWindow().getWindow(),key,0,GLFW.GLFW_PRESS,0);keyboard.posefixtures$press(mc.getWindow().getWindow(),key,0,GLFW.GLFW_RELEASE,0);}
    private void repeatKey(Minecraft mc,int key) {((KeyboardAccess)(Object)mc.keyboardHandler).posefixtures$press(mc.getWindow().getWindow(),key,0,GLFW.GLFW_REPEAT,0);}
    private void assertReturned(Minecraft mc,StudioState s,String action) {check(mc.screen instanceof StudioScreen && s.active && !s.capture && s.mode==StudioState.Mode.POSE,action+" returns to the last Pose editor");check(s.actor().id.equals(mc.player.getUUID()) && s.bone.equals("left_elbow") && s.actor().bones.get(s.bone).rotation[0]==elbow,action+" preserves selection and joint pose");check(s.actor().transform.equals(preserved.get(0).transform()) && s.actor().frozen,action+" preserves actor position and freeze");checkCamera(s,action+" preserves all camera parameters");}
    private void saveCamera(StudioState s) {camera=new Vec3(s.camera.x,s.camera.y,s.camera.z);yaw=s.camera.yaw;pitch=s.camera.pitch;roll=s.camera.roll;fov=s.camera.fov;}
    private void checkCamera(StudioState s,String action) {check(camera.distanceTo(new Vec3(s.camera.x,s.camera.y,s.camera.z))<.00001 && yaw==s.camera.yaw && pitch==s.camera.pitch && roll==s.camera.roll && fov==s.camera.fov,action);}
    private void applyRotation(Minecraft mc,double value) {var fields=mc.screen.children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).toList();fields.get(0).setValue(Double.toString(value));press(mc,"posestudio.ui.apply");}
    private void press(Minecraft mc,String key) {String label=net.minecraft.client.resources.language.I18n.get(key);((Button)mc.screen.children().stream().filter(w->w instanceof Button b && b.getMessage().getString().equals(label)).findFirst().orElseThrow()).onPress();}
    private void screenshot(Minecraft mc,String name) throws Exception {try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(evidence.resolve(name));}}
}
