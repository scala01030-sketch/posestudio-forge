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
import studio.pose.client.*;
import studio.pose.data.*;
import studio.pose.ui.*;


public final class PlacementReadyRun {
    private static final String[] TYPES={"minecraft:chicken","minecraft:skeleton","minecraft:horse","tacz:target_minecart","lrtactical:smoke_grenade"};
    private final Path evidence=Path.of(System.getProperty("posestudio.acceptance.evidence"));
    private final List<String> checks=new ArrayList<>();
    private final long start=System.nanoTime();
    private int stage,ticks,index;
    private StudioScreen returned;
    private UUID id;
    private String part;
    private BonePose original;
    private ActorTransform transform;
    private void next(int value) {stage=value;ticks=0;}
    private void check(boolean success,String name) {
        if(!success) throw new AssertionError(TYPES[index]+": "+name);
        checks.add("PASS "+TYPES[index]+": "+name);System.out.println("POSE_PLACEMENT "+checks.get(checks.size()-1));
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();StudioState s=StudioState.INSTANCE;ticks++;
        try {
            if(System.nanoTime()-start>480_000_000_000L || (stage>1 && ticks>300)) throw new AssertionError("Placement readiness timeout stage "+stage+" "+s.message);
            switch(stage) {
                case 0 -> {
                    if(mc.screen==null || mc.getOverlay()!=null || ticks<20) return;
                    Files.createDirectories(evidence);mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);
                    mc.createWorldOpenFlows().createFreshLevel("PosePlacement-"+System.currentTimeMillis(),new LevelSettings("PosePlacement",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());next(1);
                }
                case 1 -> {
                    if(mc.player==null || mc.screen!=null || ticks<30) return;
                    mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.connection.teleport(0,-60,0,180,0);p.serverLevel().setDayTime(6000);p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,mc.getSingleplayerServer());}).join();s.toggle();next(2);
                }
                case 2 -> {
                    if(!s.active || !(mc.screen instanceof StudioScreen)) return;
                    s.camera.x=0;s.camera.y=-58.8;s.camera.z=-5;s.camera.yaw=0;s.camera.pitch=0;s.camera.roll=0;s.camera.fov=65;
                    press(mc,"posestudio.ui.pose");press(mc,"posestudio.ui.scene");var boxes=fields(mc);boxes.get(0).setValue(TYPES[index]);mc.screen.mouseClicked(15,65,0);
                    double[] v={0,-60,0,180,0};for(int i=0;i<5;i++) boxes.get(i+1).setValue(Double.toString(v[i]));
                    press(mc,"posestudio.catalog.place");returned=(StudioScreen)mc.screen;press(mc,"posestudio.ui.pose");next(3);
                }
                case 3 -> {
                    var a=s.actor();Entity e=a==null?null:s.entity(a.id);
                    if(e==null || !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(TYPES[index]) || !a.frozen) return;
                    check(mc.screen==returned,"catalog returns immediately to the same usable Pose screen");id=a.id;
                    s.camera.y=e.getY()+Math.max(.5,e.getBbHeight()*.6);next(4);
                }
                case 4 -> {
                    var a=s.actor();if(a.adapterReady && a.lastMainRenderNanos==0) return;
                    check(mc.screen==returned,"model readiness needs no close, reopen or reselect");
                    if(!a.adapterReady) {
                        check(!a.adapterStatus.getString().isBlank() && a.bones.isEmpty(),"unsupported model reports fallback and leaves Actor controls usable");move(mc);next(7);return;
                    }
                    check(a.bones.containsKey(s.bone) && a.editableBones.contains(s.bone),"selected bone belongs to the rendered editable model");
                    check(matches(fields(mc),a.bones.get(s.bone)),"initial numerical fields bind to actual selected model data");
                    if(index==0) {


                        var all=new LinkedHashMap<>(a.bones);
                        part=a.editableBones.stream().filter(n->all.containsKey(n) && Arrays.stream(all.get(n).position).anyMatch(v->Math.abs(v)>.1)).findFirst().orElseThrow();
                        s.bone=part;a.bones.remove(part);returned.refresh();
                        check(!a.bones.isEmpty(),"late bone arrives after other bones, not an empty-to-nonempty transition");
                        a.bones.clear();a.bones.putAll(all);((net.minecraft.client.gui.screens.Screen)returned).tick();
                        check(matches(fields(mc),all.get(s.bone)),"late selected-bone arrival refreshes fields in the existing screen");
                        s.bone="missing/retired-model-node";((net.minecraft.client.gui.screens.Screen)returned).tick();
                        check(a.bones.containsKey(s.bone) && a.editableBones.contains(s.bone),"stale model node selection automatically falls back to a visible bone");
                        var boxes=fields(mc);EditBox focused=boxes.get(0);focused.setValue("37.125");((net.minecraft.client.gui.screens.Screen)returned).setFocused(focused);focused.setFocused(true);((net.minecraft.client.gui.screens.Screen)returned).tick();((net.minecraft.client.gui.screens.Screen)returned).tick();
                        check(fields(mc).get(0)==focused && focused.getValue().equals("37.125") && focused.isFocused(),"ordinary ticks preserve pending numeric edits and focus");
                    }
                    part=s.bone;original=a.bones.get(part).copy();var boxes=fields(mc);boxes.get(0).setValue(Double.toString(original.rotation[0]+23));press(mc,"posestudio.ui.apply");next(5);
                }
                case 5 -> {
                    if(ticks<5) return;
                    check(mc.screen==returned && Math.abs(s.actor().bones.get(part).rotation[0]-original.rotation[0]-23)<.001,"Pose apply works without rebuilding the screen");
                    check(!s.actor().nodes.isEmpty(),"edited model continues rendering with shader and model mods");
                    screenshot(mc,"pose-ready");press(mc,"posestudio.ui.undo");next(6);
                }
                case 6 -> {
                    check(Math.abs(s.actor().bones.get(part).rotation[0]-original.rotation[0])<.001,"pose undo remains available after placement");move(mc);next(7);
                }
                case 7 -> {
                    if(Math.abs(s.actor().transform.x()-transform.x()-.75)>=.001 && ticks<100) return;
                    check(Math.abs(s.actor().transform.x()-transform.x()-.75)<.001,"placed model can move using Actor controls (actual="+s.actor().transform+", message="+s.message.getString()+")");
                    press(mc,"posestudio.ui.pose");check(mc.screen==returned,"Actor to Pose switch keeps the existing editor");
                    if(s.actor().adapterReady) check(matches(fields(mc),s.actor().bones.get(s.bone)),"fields remain correct after Actor move and Pose return");
                    s.removePlaced();next(8);
                }
                case 8 -> {
                    if(s.entity(id)!=null || s.actors.containsKey(id)) return;
                    check(mc.screen==returned,"removal updates current editor without reopening");
                    if(++index<TYPES.length) next(2);else {index=TYPES.length-1;s.exit(true);next(9);}
                }
                case 9 -> {
                    if(ticks<15) return;
                    check(!s.active && s.actors.isEmpty(),"exit restores normal scene state");Files.write(evidence.resolve("acceptance.txt"),checks);System.out.println("POSE_PLACEMENT COMPLETE");mc.stop();stage=99;
                }
            }
        } catch(Throwable error) {
            error.printStackTrace();try {Files.createDirectories(evidence);Files.write(evidence.resolve("acceptance.txt"),checks);Files.writeString(evidence.resolve("failure.txt"),"stage "+stage+" "+error);Files.writeString(evidence.resolve("acceptance.txt"),"\nFAIL "+error,StandardOpenOption.APPEND);}catch(Exception ignored){}s.exit(true);mc.stop();stage=99;
        }
    }
    private void move(Minecraft mc) {press(mc,"posestudio.ui.actor");transform=StudioState.INSTANCE.actor().transform;fields(mc).get(0).setValue(Double.toString(transform.x()+.75));press(mc,"posestudio.ui.apply");}
    private boolean matches(List<EditBox> boxes,BonePose pose) {if(pose==null || boxes.size()<6) return false;for(int i=0;i<6;i++) if(Math.abs(Double.parseDouble(boxes.get(i).getValue())-(i<3?pose.rotation[i]:pose.position[i-3]))>.001) return false;return true;}
    private List<EditBox> fields(Minecraft mc) {return mc.screen.children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).toList();}
    private void press(Minecraft mc,String key) {String label=net.minecraft.client.resources.language.I18n.get(key);((Button)mc.screen.children().stream().filter(w->w instanceof Button b && b.getMessage().getString().equals(label)).findFirst().orElseThrow()).onPress();}
    private void screenshot(Minecraft mc,String label) throws Exception {try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(evidence.resolve(index+"-"+TYPES[index].replace(':','-')+"-"+label+".png"));}}
}
