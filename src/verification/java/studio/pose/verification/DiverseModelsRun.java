package studio.pose.verification;

import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import studio.pose.client.*;
import studio.pose.data.*;
import studio.pose.model.RenderContext;
import studio.pose.mixin.ModelPartAccess;
import studio.pose.ui.*;

/** Actual pack models, varied bones, transforms and controls. Never shipped in the main JAR. */
public final class DiverseModelsRun {
    private static final String[] TYPES={"minecraft:wolf","minecraft:horse","minecraft:chicken","minecraft:rabbit",
        "minecraft:creeper","minecraft:enderman",
        "tacz:target_minecart","lrtactical:smoke_grenade"};
    private int stage,ticks,index,partIndex;
    private UUID id;
    private List<String> parts=List.of();
    private Map<String,Matrix4f> before;
    private BonePose originalPose;
    private ActorTransform originalTransform;
    private String part;
    private final List<String> checks=new ArrayList<>(),report=new ArrayList<>();
    private final Path evidence=Path.of(System.getProperty("posestudio.acceptance.evidence"));
    private final long start=System.nanoTime();
    private void check(boolean success,String name) {if(!success) throw new AssertionError(TYPES[index]+": "+name);checks.add("PASS "+TYPES[index]+": "+name);System.out.println("POSE_DIVERSE "+checks.get(checks.size()-1));}
    private void next(int value) {stage=value;ticks=0;}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();StudioState s=StudioState.INSTANCE;ticks++;
        try {
            if(System.nanoTime()-start>600_000_000_000L) throw new AssertionError("Diverse timeout: "+TYPES[index]+" stage "+stage+" "+s.message);
            switch(stage) {
                case 0 -> {
                    if(mc.screen==null || mc.getOverlay()!=null || ticks<20) return;
                    Files.createDirectories(evidence);mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);
                    mc.createWorldOpenFlows().createFreshLevel("PoseDiverse-"+System.currentTimeMillis(),new LevelSettings("PoseDiverse",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());next(1);
                }
                case 1 -> {if(mc.player==null || mc.screen!=null || ticks<30) return;mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.connection.teleport(0,-60,0,180,0);p.serverLevel().setDayTime(6000);p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,mc.getSingleplayerServer());}).join();s.toggle();next(2);}
                case 2 -> {
                    if(!s.active || !(mc.screen instanceof StudioScreen)) return;
                    s.camera.x=index%3*2;s.camera.y=-58.8;s.camera.z=-5;s.camera.yaw=0;s.camera.pitch=0;s.camera.roll=0;s.camera.fov=65;
                    press(mc,"posestudio.ui.scene");var fields=fields(mc);fields.get(0).setValue(TYPES[index]);mc.screen.mouseClicked(15,65,0);
                    double[] v={index%3*2,-60,0,180,0};for(int i=0;i<5;i++) fields.get(i+1).setValue(Double.toString(v[i]));next(3);
                }
                case 3 -> {if(ticks<5) return;if(index==0 || index>=6) screenshot(mc,"catalog");press(mc,"posestudio.catalog.place");next(4);}
                case 4 -> {
                    var a=s.actor();Entity e=a==null?null:s.entity(a.id);if(e==null || !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(TYPES[index]) || !a.frozen || ticks<20) return;
                    id=e.getUUID();check(s.placed.contains(id),"catalog creation, replication and freeze");
                    s.camera.y=e.getY()+Math.max(.5,e.getBbHeight()*.6);s.camera.roll=index%3*4-4;next(5);
                }
                case 5 -> {
                    if(ticks<15) return;var a=s.actor();Entity e=s.entity(id);var screen=(StudioScreen)mc.screen;screen.rescan();
                    check(screen.listedEntities().stream().anyMatch(found->found.getUUID().equals(id)),"actor discovery");
                    screenshot(mc,"before");
                    if(!a.adapterReady) {
                        if(TYPES[index].startsWith("lrtactical:")) {
                            var item=(net.minecraft.world.item.ItemStack)e.getClass().getMethod("getItem").invoke(e);
                            var display=(Optional<?>)Class.forName("me.xjqsh.lrtactical.api.LrTacticalAPI").getMethod("getThrowableDisplay",net.minecraft.world.item.ItemStack.class).invoke(null,item);
                            check(item.hasTag() && item.getTag().contains("ThrowableId") && display.isPresent(),"native throwable item data and real display asset initialized");
                            report.add(TYPES[index]+" | DISPLAY_ASSET | "+item.getTag());
                        }
                        check(!a.adapterStatus.getString().isBlank() && a.bones.isEmpty(),"explicit no-ModelPart degradation instead of waiting for rendering");
                        report.add(TYPES[index]+" | NO_MODEL_PART | "+e.getClass().getName()+" | "+a.adapterStatus.getString());move(mc);next(9);return;
                    }
                    check(!a.nodes.isEmpty(),"actual main-pass model nodes captured");
                    Files.write(evidence.resolve(TYPES[index].replace(':','-')+"-model-tree.txt"),RenderContext.inspect(e,a).bindings.entrySet().stream().map(entry->entry.getValue()+" class="+entry.getKey().getClass().getSimpleName()+" cubes="+((ModelPartAccess)(Object)entry.getKey()).pose$cubes().size()+" default="+a.editableBones.contains(entry.getValue().name())).toList());
                    parts=a.nodes.keySet().stream().filter(a.editableBones::contains).sorted(Comparator.comparingInt((String n)->n.toLowerCase(Locale.ROOT).contains(index%2==0?"head":"leg")?0:1).thenComparing(n->n)).limit(3).toList();
                    report.add(TYPES[index]+" | MODEL_PART | "+e.getClass().getName()+" | "+parts);partIndex=0;edit(mc);next(6);
                }
                case 6 -> {
                    if(ticks<12) return;var a=s.actor();
                    check(difference(before,geometry(s.entity(id),a))>.01,"visible geometry changes for varied position and rotation: "+part);
                    screenshot(mc,"pose-"+partIndex);press(mc,"posestudio.ui.undo");next(7);
                }
                case 7 -> {
                    if(ticks<12) return;var a=s.actor();check(difference(before,geometry(s.entity(id),a))<.0001,"single undo restores visible geometry: "+part);
                    if(++partIndex<parts.size()) {edit(mc);next(6);} else {move(mc);next(9);}
                }
                case 9 -> {
                    if(ticks<15) return;var a=s.actor();check(Math.abs(a.transform.x()-(originalTransform.x()+.6))<.001 && Math.abs(a.transform.y()-(originalTransform.y()+.3))<.001,"varied XYZ and yaw/pitch Actor move acknowledged");
                    press(mc,"posestudio.ui.undo");next(10);
                }
                case 10 -> {if(ticks<15) return;check(s.actor().transform.equals(originalTransform),"Actor transform undo");s.capture();next(11);}
                case 11 -> {if(ticks<10) return;check(s.capture && mc.screen==null && s.actor().frozen,"capture retains frozen pose and hides editor");screenshot(mc,"capture");s.capture();s.removePlaced();next(12);}
                case 12 -> {
                    if(ticks<15) return;check(s.entity(id)==null && !s.actors.containsKey(id),"temporary actor removed without affecting scene");
                    mc.getSingleplayerServer().submit(()->{if(mc.getSingleplayerServer().overworld().getEntity(id)!=null) throw new AssertionError("Server actor remained");}).join();
                    if(++index<TYPES.length) next(2);else {index=TYPES.length-1;next(13);}
                }
                case 13 -> {
                    check(studio.pose.StudioConfig.maxActors()==8,"default shared scene budget is 8");s.select(mc.player);if(!s.actor().frozen) s.freeze();
                    s.move(new ActorTransform(-6,-60,4,180,0));
                    String[] scene={"minecraft:wolf","minecraft:horse","minecraft:chicken","minecraft:rabbit","minecraft:creeper","minecraft:enderman","minecraft:cow","tacz:target_minecart"};
                    for(int i=0;i<scene.length;i++) studio.pose.network.StudioNetwork.place(new ResourceLocation(scene[i]),new ActorTransform(-4.5+i*1.5,-60,4,180,0));
                    s.camera.x=-.75;s.camera.y=-57;s.camera.z=-12;s.camera.yaw=0;s.camera.pitch=7;s.camera.roll=0;s.camera.fov=65;next(14);
                }
                case 14 -> {
                    if(ticks<40 || s.actors.values().stream().filter(a->a.frozen).count()!=9) return;
                    if(s.actors.values().stream().anyMatch(a->a.lastMainRenderNanos==0) && ticks<120) return;
                    Files.write(evidence.resolve("nine-mixed-actor-render-status.txt"),s.actors.values().stream().map(a->a.id+" entity="+BuiltInRegistries.ENTITY_TYPE.getKey(s.entity(a.id).getType())+" frozen="+a.frozen+" ready="+a.adapterReady+" rendered="+(a.lastMainRenderNanos>0)+" transform="+a.transform).toList());
                    check(s.actors.values().stream().allMatch(a->a.lastMainRenderNanos>0),"nine representative actors including player and TaCZ rendered in the same scene");
                    studio.pose.network.StudioNetwork.place(new ResourceLocation("minecraft:cow"),new ActorTransform(0,-60,6,0,0));next(15);
                }
                case 15 -> {
                    if(ticks<15) return;check(s.actors.size()==9 && s.message.getString().equals(net.minecraft.client.resources.language.I18n.get("posestudio.error.placement_limit")),"ninth living actor rejected by server and explained in UI");s.capture();next(16);
                }
                case 16 -> {
                    if(ticks<10) return;screenshot(mc,"nine-mixed-actor-scene");check(s.capture && s.actors.values().stream().filter(a->a.frozen).count()==9,"capture retains nine-mixed-actor scene");s.exit(true);next(17);
                }
                case 17 -> {
                    if(ticks<20) return;mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);if(studio.pose.server.FreezeService.activeCount(p.serverLevel())!=0) throw new AssertionError("Scene budget not released");}).join();check(true,"exit releases all nine actor slots");Files.write(evidence.resolve("model-results.txt"),report);Files.write(evidence.resolve("acceptance.txt"),checks);System.out.println("POSE_DIVERSE COMPLETE");mc.stop();stage=99;
                }
            }
        } catch(Throwable error) {error.printStackTrace();try {Files.write(evidence.resolve("acceptance.txt"),checks);Files.writeString(evidence.resolve("failure.txt"),TYPES[index]+" stage "+stage+" "+error);Files.writeString(evidence.resolve("acceptance.txt"),"\nFAIL "+error,StandardOpenOption.APPEND);Files.write(evidence.resolve("model-results.txt"),report);}catch(Exception ignored){}s.exit(true);mc.stop();stage=99;}
    }
    private void edit(Minecraft mc) {
        var s=StudioState.INSTANCE;part=parts.get(partIndex);s.mode=StudioState.Mode.POSE;s.bone=part;mc.setScreen(new StudioScreen());
        before=geometry(s.entity(id),s.actor());originalPose=s.actor().bones.get(part).copy();var boxes=fields(mc);
        double[] rotation={12+index*2,-24+partIndex*13,18-index*3};
        for(int i=0;i<3;i++) boxes.get(i).setValue(Double.toString(rotation[i]));
        boxes.get(3+partIndex).setValue(Double.toString(originalPose.position[partIndex]+(index%2==0?.8:-.6)));press(mc,"posestudio.ui.apply");
    }
    private void move(Minecraft mc) {
        var s=StudioState.INSTANCE;s.mode=StudioState.Mode.ACTOR;mc.setScreen(new StudioScreen());originalTransform=s.actor().transform;var boxes=fields(mc);
        double[] v={originalTransform.x()+.6,originalTransform.y()+.3,originalTransform.z()-.8,125+index*9,index%2==0?8:-7};
        for(int i=0;i<5;i++) boxes.get(i).setValue(Double.toString(v[i]));press(mc,"posestudio.ui.apply");
    }
    private Map<String,Matrix4f> geometry(Entity entity,ActorState actor) {
        Map<String,Matrix4f> result=new LinkedHashMap<>();var adapter=RenderContext.inspect(entity,actor);
        for(var entry:adapter.bindings.entrySet()) if(!((ModelPartAccess)(Object)entry.getKey()).pose$cubes().isEmpty()) {
            var node=actor.nodes.get(entry.getValue().name());if(node!=null) result.put(node.name(),new Matrix4f(node.matrix()));
        }
        return result;
    }
    private double difference(Map<String,Matrix4f> a,Map<String,Matrix4f> b) {
        double result=0;for(var entry:a.entrySet()) {if(!b.containsKey(entry.getKey())) return Double.POSITIVE_INFINITY;float[] x=new float[16],y=new float[16];entry.getValue().get(x);b.get(entry.getKey()).get(y);for(int i=0;i<16;i++) result=Math.max(result,Math.abs(x[i]-y[i]));}return result;
    }
    private List<EditBox> fields(Minecraft mc) {return mc.screen.children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).toList();}
    private void press(Minecraft mc,String key) {String label=net.minecraft.client.resources.language.I18n.get(key);((Button)mc.screen.children().stream().filter(w->w instanceof Button b && b.getMessage().getString().equals(label)).findFirst().orElseThrow()).onPress();}
    private void screenshot(Minecraft mc,String label) throws Exception {try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(evidence.resolve(String.format(Locale.ROOT,"%02d-%s-%s.png",index,TYPES[index].replace(':','-'),label)));}}
}
