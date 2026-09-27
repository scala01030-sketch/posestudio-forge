package studio.pose.verification;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import studio.pose.client.*;
import studio.pose.data.*;
import studio.pose.server.FreezeService;
import studio.pose.serialization.PoseSerializer;
import studio.pose.ui.StudioScreen;
import studio.pose.network.StudioNetwork;
import studio.pose.overlay.StudioOverlay;
import net.minecraft.world.item.*;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** Executed in a fresh integrated world. Excluded from the release jar. */
public final class AcceptanceRun {
    private int stage,ticks;
    private long start=System.nanoTime();
    private CompletableFuture<?> pending;
    private UUID vanilla,mod,skeleton,placedMod,actualPackActor;
    private org.joml.Matrix4f packPartBefore;
    private String packPart;
    private final List<UUID> nearby=new ArrayList<>();
    private org.joml.Matrix4f headBefore;
    private String visibleHead;
    private Vec3 original;
    private int frozenTick;
    private int jointIndex;
    private static final String[] JOINTS={"left_elbow","right_elbow","left_knee","right_knee"};
    private BonePose jointBefore;
    private double baseBefore,outerBefore;
    private final List<String> results=new ArrayList<>();
    private final Path evidence=Path.of(System.getProperty("posestudio.acceptance.evidence","evidence"));
    private void check(boolean value,String name) { if(!value) throw new AssertionError(name);results.add("PASS "+name);System.out.println("POSE_ACCEPTANCE PASS "+name); }
    private void advance() { stage++;ticks=0; }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("posestudio.acceptance")) return;
        Minecraft mc=Minecraft.getInstance();StudioState s=StudioState.INSTANCE;ticks++;
        try {
            if(System.nanoTime()-start>240_000_000_000L) throw new AssertionError("Acceptance run timeout at stage "+stage+" status "+s.message);
            switch(stage) {
                case 0 -> {
                    if(mc.screen==null || mc.getOverlay()!=null || ticks<20) return;
                    System.out.println("POSE_ACCEPTANCE creating world from "+mc.screen.getClass().getSimpleName());
                    Files.createDirectories(evidence);mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);if(!Boolean.getBoolean("posestudio.acceptance.exact")) mc.options.guiScale().set(2);mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);mc.resizeDisplay();
                    String world="PoseAcceptance-"+System.currentTimeMillis();
                    mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings(world,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());advance();
                }
                case 1 -> {
                    if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null || mc.screen!=null) return;
                    pending=mc.getSingleplayerServer().submit(()-> {
                        var server=mc.getSingleplayerServer();ServerPlayer p=server.getPlayerList().getPlayers().get(0);var level=p.serverLevel();
                        p.connection.teleport(0,-60,0,180,0);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                        Pig pig=EntityType.PIG.create(level);pig.moveTo(-4,-60,0,180,0);level.addFreshEntity(pig);vanilla=pig.getUUID();
                        for(var type:List.of(EntityType.COW,EntityType.SHEEP,EntityType.CHICKEN)) {Entity extra=type.create(level);extra.moveTo(5,-60,3+nearby.size(),0,0);if(extra instanceof Mob m) m.setNoAi(true);level.addFreshEntity(extra);nearby.add(extra.getUUID());}
                        var other=Fixtures.PIG.get().create(level);other.moveTo(4,-60,0,180,0);level.addFreshEntity(other);mod=other.getUUID();
                    });advance();
                }
                case 2 -> { if(!pending.isDone() || ticks<30) return;pending.join();s.toggle();advance(); }
                case 3 -> {
                    if(!s.active) return;
                    check(mc.screen instanceof StudioScreen,"Studio entry and UI");
                    if(Boolean.getBoolean("posestudio.acceptance.exact")) {
                        var expected=Files.readAllLines(Path.of(System.getProperty("posestudio.acceptance.expectedPacksFile"))).stream().filter(p->p.startsWith("file/")).toList();
                        Files.write(evidence.resolve("active-resource-packs.txt"),mc.options.resourcePacks);
                        check(!expected.isEmpty() && mc.options.resourcePacks.containsAll(expected),"Current user-selected resource packs actually enabled");
                    }
                    boolean chinese=mc.options.languageCode.equals("zh_cn");
                    check(mc.options.languageCode.equals(System.getProperty("posestudio.acceptance.language","en_us")),"Requested language actually loaded");
                    check(net.minecraft.client.resources.language.I18n.get("posestudio.ui.freeze").equals(chinese?"冻结":"Freeze"),"Selected language button translation");
                    check(studio.pose.ui.StudioText.bone("left_elbow").equals(chinese?"左肘":"Left elbow"),"Selected language custom joint translation");
                    check(studio.pose.ui.StudioText.bone("root/mod_unknown_bone").endsWith("mod_unknown_bone"),"Unknown mod bone identifier preserved");
                    check(net.minecraft.network.chat.Component.translatable("posestudio.error.permission").getString().equals(net.minecraft.client.resources.language.I18n.get("posestudio.error.permission")),"Client translation of server error key");
                    s.camera.x=0;s.camera.y=-58.8;s.camera.z=-4;s.camera.yaw=0;s.camera.pitch=0;s.camera.fov=50;
                    s.select(mc.player);
                    check(s.actor().adapterReady && s.actor().frozen,"Own player is automatically frozen on entry and model discovery completes");
                    original=mc.player.position();if(!s.actor().frozen) s.freeze();advance();
                }
                case 4 -> {
                    var a=s.actor();if(a==null || !a.frozen || a.bones.size()<10 || ticks<20) return;
                    for(var part:net.minecraft.world.entity.player.PlayerModelPart.values()) if(!mc.options.isModelPartEnabled(part)) mc.options.toggleModelPart(part,true);
                    check(a.bones.containsKey("left_elbow") && a.bones.containsKey("right_knee"),"Player custom joints discovered");
                    a.bones.get("left_arm").rotation[2]=-35;a.bones.get("right_arm").rotation[2]=25;
                    a.bones.get("left_arm").position[0]+=.5;a.bones.get("right_arm").position[1]+=.25;
                    a.bones.get("left_leg").rotation[2]=-10;a.bones.get("right_leg").rotation[2]=10;
                    a.bones.get("right_leg").position[2]+=.25;
                    a.bones.get("head").rotation[1]=20;a.bones.get("body").rotation[2]=10;
                    a.bones.get("left_elbow").rotation[0]=-85;a.bones.get("right_elbow").rotation[0]=-100;
                    a.bones.get("left_knee").rotation[0]=70;a.bones.get("right_knee").rotation[0]=40;
                    s.mode=StudioState.Mode.POSE;s.rotationAxis=2;mc.setScreen(new StudioScreen());advance();
                }
                case 5 -> {
                    if(ticks<25) return;
                    var a=s.actor();
                    StudioOverlay.Point headPoint=StudioOverlay.jointPoint("head"),bodyPoint=StudioOverlay.jointPoint("body");
                    check(headPoint!=null && bodyPoint!=null && Math.hypot(headPoint.x()-bodyPoint.x(),headPoint.y()-bodyPoint.y())>12,"Head and torso handles are separate usable targets in the actual player viewport");
                    check(StudioOverlay.click(bodyPoint.x(),bodyPoint.y()) && s.bone.equals("body"),"Mouse picks torso center without selecting head");
                    check(StudioOverlay.click(headPoint.x(),headPoint.y()) && s.bone.equals("head"),"Mouse picks face center without selecting torso");
                    System.out.println("POSE_ACCEPTANCE player render diagnostics: bones="+a.bones.keySet()+" nodes="+a.nodes.keySet()+" bentVertices="+a.bentVertices+" adapter="+a.adapterStatus.getString());
                    var adapter=studio.pose.model.RenderContext.inspect(mc.player,a);
                    if(Boolean.getBoolean("posestudio.acceptance.exact") && mc.options.resourcePacks.contains("file/FA+Player-v1.1.zip")) check(adapter.bindings.keySet().stream().anyMatch(p->p.getClass().getName().contains("EMFModelPartCustom")),"Actual CEM child geometry is loaded, not a vanilla replacement");
                    else check(adapter.player && !adapter.bindings.isEmpty(),"Current player model exposes its actual existing limb geometry");
                    List<String> tree=new ArrayList<>();
                    for(var entry:adapter.bindings.entrySet()) {
                        var part=entry.getKey();var access=(studio.pose.mixin.ModelPartAccess)(Object)part;
                        tree.add(entry.getValue()+" class="+part.getClass().getName()+" cubes="+access.pose$cubes().size()+" xyz="+part.x+","+part.y+","+part.z+" rotations="+part.xRot+","+part.yRot+","+part.zRot+" children="+access.pose$children().keySet());
                    }
                    Files.write(evidence.resolve("player-model-tree.txt"),tree);
                    screenshot(mc,"00-player-render-diagnostic.png");
                    check(a.nodes.containsKey("left_elbow") && a.nodes.containsKey("right_knee"),"Player rendered nodes and continuous mesh path");
                    check(a.adapterReady,"Player skin mesh introspection");
                    System.out.println("POSE_ACCEPTANCE skin transform errors="+a.skinTransformErrors);
                    check(a.skinTransformErrors.size()==6 && a.skinTransformErrors.values().stream().allMatch(e->e<.0001),"All six skin overlays share base position, rotation and scale matrices");
                    check(a.bentVertices>1000,"Bent player limb vertices actually emitted in the main render pass");
                    System.out.println("POSE_ACCEPTANCE skin="+mc.player.getModelName()+" overlay vertices="+a.skinLayerVertices);
                    check(mc.player.getSkinTextureLocation().getNamespace().equals("posefixtures"),"Explicit two-layer 64x64 test skin loaded");
                    boolean threeD=net.minecraftforge.fml.ModList.get().isLoaded("skinlayers3d");
                    for(String limb:List.of("left_arm","right_arm","left_leg","right_leg"))
                        check(a.skinLayerVertices.getOrDefault(limb,0)+a.skinLayerVertices.getOrDefault("flat/"+limb,0)>0,"Bent outer skin emitted: "+limb);
                    check(a.bones.keySet().stream().noneMatch(n->n.contains("Sleeve") || n.contains("Pants") || n.equals("jacket")),"Skin layers share canonical editable bones");
                    var savedTransforms=studio.pose.model.PoseController.class.getDeclaredField("SAVED");savedTransforms.setAccessible(true);
                    check(((Deque<?>)savedTransforms.get(null)).isEmpty(),"Shared ModelPart transforms restored with no retained stack entries");
                    s.rotationAxis=2;var handle=StudioOverlay.rotationHandle(2,.37);var end=StudioOverlay.rotationHandle(2,1.0);
                    BonePose before=a.bones.get(s.bone).copy();
                    check(handle!=null && end!=null && StudioOverlay.click(handle.x(),handle.y()) && StudioOverlay.drag(end.x(),end.y()),"Projected rotation gizmo picking and drag");
                    check(java.util.stream.IntStream.range(0,3).anyMatch(i->Math.abs(a.bones.get(s.bone).rotation[i]-before.rotation[i])>1),"Gizmo changes the selected bone rotation");
                    StudioOverlay.release();a.bones.put(s.bone,before);
                    testMouse(mc,s);
                    screenshot(mc,"01-player-editor.png");
                    PoseSerializer.save(evidence,"player-pose","minecraft:player",a);check(PoseSerializer.load(evidence,"player-pose").bones.get("left_elbow").rotation[0]==-85,"In-game JSON round trip");
                    s.rotationAxis=0;mc.setScreen(new StudioScreen());stage=20;ticks=0;
                }
                case 6 -> {
                    if(ticks<25) return;
                    check(Math.abs(mc.player.getX()-.5)<.001 && Math.abs(mc.player.getYRot()-165)<.001,"Actor position and rotation server round trip");
                    screenshot(mc,"01b-player-side.png");
                    Entity target=s.entity(vanilla);check(target!=null,"Vanilla actor replicated to client");
                    s.camera.x=target.getX();s.camera.y=target.getY()+.7;s.camera.z=target.getZ()-3;s.camera.yaw=0;
                    s.select(target);if(!s.actor().frozen) s.freeze();advance();
                }
                case 7 -> {
                    if(ticks%40==0) System.out.println("POSE_ACCEPTANCE waiting vanilla: "+s.message+" "+s.actor().adapterStatus+" frozen="+s.actor().frozen+" parts="+s.actor().bones.size());
                    var a=s.actor();if(a==null || !a.frozen || a.bones.size()<4) return;
                    check(a.bones.size()>=4,"Vanilla ModelPart adapter");String key=a.bones.keySet().iterator().next();a.bones.get(key).rotation[2]=30;s.bone=key;mc.setScreen(new StudioScreen());
                    pending=mc.getSingleplayerServer().submit(()-> { Entity e=mc.getSingleplayerServer().overworld().getEntity(vanilla);frozenTick=e.tickCount; });advance();
                }
                case 8 -> {
                    if(ticks<30 || !pending.isDone()) return;pending.join();screenshot(mc,"02-vanilla-editor.png");
                    pending=mc.getSingleplayerServer().submit(()-> { Entity e=mc.getSingleplayerServer().overworld().getEntity(vanilla);if(e.tickCount!=frozenTick || !((Mob)e).isNoAi() || !e.isNoGravity()) throw new AssertionError("Frozen entity kept ticking"); });advance();
                }
                case 9 -> {
                    if(!pending.isDone()) return;pending.join();check(true,"Server tick suppression, AI and gravity freeze");
                    Entity target=s.entity(mod);check(target!=null,"Mod actor replicated to client");
                    s.camera.x=target.getX();s.camera.y=target.getY()+.7;s.camera.z=target.getZ()-3;s.camera.yaw=0;
                    s.select(target);if(!s.actor().frozen) s.freeze();advance();
                }
                case 10 -> {
                    var a=s.actor();if(a==null || !a.frozen || a.bones.size()<4) return;
                    check(a.bones.size()>=4,"Mod entity ModelPart adapter (test-only registered entity)");String key=a.bones.keySet().iterator().next();a.bones.get(key).rotation[0]=25;s.bone=key;mc.setScreen(new StudioScreen());advance();
                }
                case 11 -> {
                    if(ticks<25) return;screenshot(mc,"03-mod-editor.png");
                    if(skeleton==null) {stage=30;ticks=0;return;}
                    s.camera.roll=12;s.camera.fov=60;s.camera.x=1;s.camera.y=-58.5;s.camera.z=-4.5;s.camera.yaw=10;s.camera.pitch=5;s.capture();
                    KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_E));advance();
                }
                case 12 -> {
                    if(ticks<20) return;
                    var camera=mc.gameRenderer.getMainCamera();check(camera.getPosition().distanceTo(new Vec3(1,-58.5,-4.5))<.0001,"Detached camera XYZ overrides");
                    check(Math.abs(camera.getYRot()-10)<.001 && Math.abs(camera.getXRot()-5)<.001 && s.camera.roll==12 && s.camera.fov==60,"Camera pitch yaw roll FOV state");
                    check(s.capture && mc.screen==null && mc.options.hideGui && s.actors.values().stream().allMatch(a->a.frozen),"Capture hides editor and retains scene");screenshot(mc,"04-capture.png");
                    check(mc.options.keyInventory.consumeClick()==false && mc.screen==null,"Camera E input does not open player inventory");
                    s.camera.x=0;s.camera.y=-63.5;s.camera.z=0;advance();
                }
                case 13 -> {
                    if(ticks<10) return;
                    check(mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(0,-63.5,0))<.0001,"Free camera can enter solid blocks without collision");
                    // Assert restoration before resumed AI, collision and live input can move the player.
                    // Network exit and equipment restoration are exercised by the other actual-instance runs.
                    pending=mc.getSingleplayerServer().submit(()-> {
                        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);FreezeService.end(p.getUUID());
                        if(p.position().distanceTo(original)>.1) throw new AssertionError("Player original transform not restored immediately: original="+original+" current="+p.position());
                    });
                    s.exit(true);advance();
                }
                case 14 -> {
                    if(ticks<25 || !pending.isDone()) return;pending.join();
                    check(!s.active && !mc.options.hideGui,"Studio exit restores UI");
                    pending=mc.getSingleplayerServer().submit(()-> {
                        if(mc.getSingleplayerServer().overworld().getEntity(skeleton)!=null) throw new AssertionError("Temporary skeleton remained after Studio exit");
                        Entity e=mc.getSingleplayerServer().overworld().getEntity(vanilla);if(FreezeService.frozen(e) || ((Mob)e).isNoAi() || e.isNoGravity()) throw new AssertionError("Actor restoration failed");
                    });advance();
                }
                case 15 -> { if(!pending.isDone()) return;pending.join();check(true,"Server actor restoration after exit");Files.write(evidence.resolve("acceptance.txt"),results);System.out.println("POSE_ACCEPTANCE COMPLETE");mc.stop();stage=99; }
                case 30 -> {
                    s.camera.x=0;s.camera.y=-58.8;s.camera.z=-4;s.camera.yaw=0;s.camera.pitch=0;s.camera.roll=0;s.camera.fov=50;
                    press(mc,"posestudio.ui.scene");
                    check(mc.screen instanceof studio.pose.ui.EntityCatalogScreen,"Scene opens searchable entity registry catalog");
                    var registry=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
                    Files.write(evidence.resolve("entity-catalog.txt"),registry.keySet().stream().sorted().map(Object::toString).toList());
                    check(registry.keySet().stream().anyMatch(id->id.getNamespace().equals("tacz")),"Actual pack Mod entity types available in registry catalog");
                    catalogPlace(mc,"minecraft:skeleton");advance();
                }
                case 31 -> {
                    var a=s.actor();Entity e=a==null?null:s.entity(a.id);
                    if(e==null || e.getType()!=EntityType.SKELETON || !a.frozen || ticks<25) return;
                    skeleton=e.getUUID();check(s.placed.contains(skeleton),"Catalog skeleton creation, client replication and automatic freeze");
                    s.mode=StudioState.Mode.POSE;s.bone="head";mc.setScreen(new StudioScreen());
                    var adapter=studio.pose.model.RenderContext.inspect(e,a);List<String> tree=new ArrayList<>();
                    for(var entry:adapter.bindings.entrySet()) {
                        var part=entry.getKey();var access=(studio.pose.mixin.ModelPartAccess)(Object)part;
                        tree.add(entry.getValue()+" class="+part.getClass().getName()+" cubes="+access.pose$cubes().size()+" xyz="+part.x+","+part.y+","+part.z);
                    }
                    Files.write(evidence.resolve("skeleton-model-tree.txt"),tree);
                    visibleHead=adapter.bindings.entrySet().stream().filter(entry->entry.getValue().name().startsWith("head/") && !((studio.pose.mixin.ModelPartAccess)(Object)entry.getKey()).pose$cubes().isEmpty())
                        .map(entry->entry.getValue().name()).filter(a.nodes::containsKey).findFirst().orElseThrow();
                    headBefore=new org.joml.Matrix4f(a.nodes.get(visibleHead).matrix());
                    check(adapter.bindings.get(((net.minecraft.client.model.HumanoidModel<?>)((net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?>)mc.getEntityRenderDispatcher().getRenderer(e)).getModel()).hat).name().equals("head"),"Fresh Animations headwear uses shared editable head transform");
                    screenshot(mc,"05-skeleton-head-before.png");
                    var fields=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).map(w->(net.minecraft.client.gui.components.EditBox)w).toList();
                    fields.get(0).setValue("25");fields.get(1).setValue("40");fields.get(2).setValue("15");fields.get(3).setValue("3");fields.get(4).setValue("-2");fields.get(5).setValue("1");press(mc,"posestudio.ui.apply");advance();
                }
                case 32 -> {
                    if(ticks<20) return;var a=s.actor();
                    check(matrixDifference(headBefore,a.nodes.get(visibleHead).matrix())>.1,"Skeleton visible head geometry changes after numerical position and XYZ rotation edit");
                    screenshot(mc,"06-skeleton-head-edited.png");press(mc,"posestudio.ui.undo");advance();
                }
                case 33 -> {
                    if(ticks<20) return;var a=s.actor();
                    check(matrixDifference(headBefore,a.nodes.get(visibleHead).matrix())<.0001,"Undo restores actual visible skeleton head geometry");
                    var playerActor=s.actors.get(mc.player.getUUID());var oldTransform=playerActor.transform;
                    playerActor.transform=new ActorTransform(200,-60,0,0,0);mc.player.setPos(200,-60,0);
                    var screen=(StudioScreen)mc.screen;screen.rescan();
                    check(screen.listedEntities().stream().anyMatch(e->e.getUUID().equals(vanilla)) && screen.listedEntities().stream().anyMatch(e->e.getUUID().equals(mod)) && screen.listedEntities().stream().anyMatch(e->e.getUUID().equals(skeleton)),"Loaded entities remain discoverable when camera is detached from distant player body");
                    check(nearby.stream().allMatch(id->screen.listedEntities().stream().anyMatch(e->e.getUUID().equals(id))),"Cow, sheep and chicken remain visible in loaded actor discovery list");
                    playerActor.transform=oldTransform;mc.player.setPos(oldTransform.x(),oldTransform.y(),oldTransform.z());
                    s.move(new ActorTransform(1,-60,0,135,0));advance();
                }
                case 34 -> {
                    if(ticks<20) return;
                    check(s.actor().transform.x()==1 && Math.abs(s.entity(skeleton).getYRot()-135)<.001,"Catalog actor can be moved and rotated through server round trip");
                    press(mc,"posestudio.ui.scene");catalogPlace(mc,"posefixtures:modelpart_pig");advance();
                }
                case 35 -> {
                    var a=s.actor();Entity e=a==null?null:s.entity(a.id);if(e==null || e.getType()!=Fixtures.PIG.get() || !s.placed.contains(a.id) || a.bones.size()<4 || ticks<20) return;
                    placedMod=a.id;check(a.frozen && a.adapterReady,"Catalog Mod actor creation and existing ModelPart adapter");
                    s.mode=StudioState.Mode.POSE;s.bone=a.bones.keySet().iterator().next();var fields=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).map(w->(net.minecraft.client.gui.components.EditBox)w).toList();
                    mc.setScreen(new StudioScreen());s.actor().bones.get(s.bone).rotation[2]=20;
                    screenshot(mc,"07-catalog-mod-actor.png");s.removePlaced();advance();
                }
                case 36 -> {
                    if(ticks<20) return;
                    check(s.entity(placedMod)==null && !s.actors.containsKey(placedMod),"Remove cleans up only the added catalog actor");
                    s.select(s.entity(mod));pending=mc.getSingleplayerServer().submit(()-> {
                        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        var orphan=EntityType.COW.create(p.serverLevel());orphan.addTag("posestudio:temporary_actor");orphan.moveTo(0,-60,0,0,0);p.serverLevel().addFreshEntity(orphan);if(p.serverLevel().getEntity(orphan.getUUID())!=null) throw new AssertionError("Saved temporary orphan was not filtered");
                        if(!FreezeService.removePlaced(p,vanilla).equals("posestudio.error.not_placed")) throw new AssertionError("Existing actor deletion guard failed");
                        if(!FreezeService.place(p,new net.minecraft.resources.ResourceLocation("minecraft:player"),new ActorTransform(0,-60,0,0,0)).error().equals("posestudio.error.create_entity")) throw new AssertionError("Noncreateable player type not rejected");
                        if(!FreezeService.place(p,new net.minecraft.resources.ResourceLocation("minecraft:cow"),new ActorTransform(10000,-60,0,0,0)).error().equals("posestudio.error.transform_area")) throw new AssertionError("Unloaded far placement guard failed");
                    });advance();
                }
                case 37 -> {if(!pending.isDone()) return;pending.join();check(true,"Placement rejects noncreateable types, far positions and deletion of original actors");
                    mc.getSingleplayerServer().submit(()-> {
                        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);int previous=studio.pose.StudioConfig.maxActors();
                        for(int limit:new int[]{5,8,10}) {
                            studio.pose.StudioConfig.MAX_ACTORS.set(limit);List<UUID> added=new ArrayList<>();
                            try {
                                while(FreezeService.activeCount(p.serverLevel())-FreezeService.propCount(p.serverLevel())<limit) {var result=FreezeService.place(p,new net.minecraft.resources.ResourceLocation("minecraft:cow"),new ActorTransform(2,-60,3,0,0));if(!result.error().isEmpty()) throw new AssertionError("Capacity fill failed");added.add(result.actor());}
                                if(!FreezeService.place(p,new net.minecraft.resources.ResourceLocation("minecraft:cow"),new ActorTransform(2,-60,3,0,0)).error().equals("posestudio.error.placement_limit")) throw new AssertionError("Placement exceeded "+limit);
                                if(!FreezeService.acquire(p,nearby.get(0)).equals("posestudio.error.placement_limit")) throw new AssertionError("Existing actor freeze exceeded "+limit);
                                if(!FreezeService.acquire(p,vanilla).isEmpty()) throw new AssertionError("Already frozen actor consumed another slot");
                            } finally {for(UUID created:added) FreezeService.removePlaced(p,created);}
                        }
                        studio.pose.StudioConfig.MAX_ACTORS.set(previous);
                    }).join();check(true,"Server shared budget 5, 8 and 10 enforces combined freeze and placement counts without duplicate slots");advance();}
                case 38 -> {
                    s.camera.x=6;s.camera.y=-59;s.camera.z=-4;s.camera.yaw=0;s.camera.pitch=0;
                    press(mc,"posestudio.ui.scene");
                    var search=(net.minecraft.client.gui.components.EditBox)mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();search.setValue("tacz:target_minecart");
                    mc.screen.mouseClicked(15,65,0);stage=43;ticks=0;
                }
                case 43 -> {
                    if(ticks<10) return;screenshot(mc,"08-actual-pack-catalog.png");press(mc,"posestudio.catalog.place");stage=39;ticks=0;
                }
                case 39 -> {
                    var a=s.actor();Entity e=a==null?null:s.entity(a.id);if(e==null || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("tacz:target_minecart") || ticks<25 || !a.frozen) return;
                    actualPackActor=a.id;check(a.adapterReady && !a.bones.isEmpty(),"Actual TaCZ target minecart creates, freezes and exposes existing ModelPart nodes");
                    packPart=a.nodes.keySet().iterator().next();s.mode=StudioState.Mode.POSE;s.bone=packPart;mc.setScreen(new StudioScreen());packPartBefore=new org.joml.Matrix4f(a.nodes.get(packPart).matrix());
                    var fields=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).map(w->(net.minecraft.client.gui.components.EditBox)w).toList();fields.get(2).setValue("35");press(mc,"posestudio.ui.apply");advance();
                }
                case 40 -> {
                    if(ticks<20) return;check(matrixDifference(packPartBefore,s.actor().nodes.get(packPart).matrix())>.1,"Actual TaCZ model part render transform changes through Pose UI");screenshot(mc,"09-actual-tacz-model-pose.png");s.removePlaced();advance();
                }
                case 41 -> {if(ticks<20) return;check(s.entity(actualPackActor)==null,"Actual TaCZ catalog actor removal");s.select(s.entity(mod));stage=11;ticks=0;}
                case 20 -> {
                    if(ticks<12) return;var a=s.actor();String joint=JOINTS[jointIndex],limb=limb(joint);
                    jointBefore=a.bones.get(joint).copy();baseBefore=signature(a,"base/"+limb);outerBefore=signature(a,"flat/"+limb);
                    var point=StudioOverlay.jointPoint(joint);
                    check(point!=null && mc.screen.mouseClicked(point.x(),point.y(),0) && s.bone.equals(joint),"Mouse selects actual joint node: "+joint);
                    var fields=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).map(w->(net.minecraft.client.gui.components.EditBox)w).toList();
                    check(fields.size()>=3,"Joint numerical rotation fields available: "+joint);
                    fields.get(0).setValue(joint.contains("elbow")?"-45":"25");press(mc,"posestudio.ui.apply");advance();
                }
                case 21 -> {
                    if(ticks<12) return;var a=s.actor();String joint=JOINTS[jointIndex],limb=limb(joint);
                    check(a.bones.get(joint).rotation[0]==(joint.contains("elbow")?-45:25),"Apply button changes joint: "+joint);
                    check(Math.abs(signature(a,"base/"+limb)-baseBefore)>1,"Numerical joint changes visible base mesh: "+joint);
                    check(Math.abs(signature(a,"flat/"+limb)-outerBefore)>1,"Numerical joint changes visible outer mesh: "+joint);
                    press(mc,"posestudio.ui.undo");check(a.bones.get(joint).rotation[0]==jointBefore.rotation[0],"Numerical adjustment undo: "+joint);advance();
                }
                case 22 -> {
                    if(ticks<12) return;var a=s.actor();String joint=JOINTS[jointIndex],limb=limb(joint);
                    check(Math.abs(signature(a,"base/"+limb)-baseBefore)<.1 && Math.abs(signature(a,"flat/"+limb)-outerBefore)<.1,"Undo restores rendered base and outer mesh: "+joint);
                    check(s.undo.size()<=10,"Undo history stays within ten adjustments: "+joint);advance();
                }
                case 23 -> {
                    if(ticks<12) return;var a=s.actor();String joint=JOINTS[jointIndex];
                    var handle=StudioOverlay.rotationHandle(0,.37);
                    check(handle!=null && mc.screen.mouseClicked(handle.x(),handle.y(),0),"X ring picking for elbow or knee: "+joint);
                    mc.screen.mouseDragged(handle.x()+18,handle.y()-12,0,18,-12);
                    mc.screen.mouseDragged(handle.x()+26,handle.y()-16,0,8,-4);
                    mc.screen.mouseReleased(handle.x()+26,handle.y()-16,0);
                    check(Math.abs(a.bones.get(joint).rotation[0]-jointBefore.rotation[0])>1,"Joint gizmo acts from the front camera view: "+joint);advance();
                }
                case 24 -> {
                    if(ticks<12) return;var a=s.actor();String joint=JOINTS[jointIndex],limb=limb(joint);
                    check(Math.abs(signature(a,"base/"+limb)-baseBefore)>1 && Math.abs(signature(a,"flat/"+limb)-outerBefore)>1,"Joint gizmo changes both rendered skin layers: "+joint);
                    press(mc,"posestudio.ui.undo");check(a.bones.get(joint).rotation[0]==jointBefore.rotation[0],"One undo restores the entire multi-event drag: "+joint);advance();
                }
                case 25 -> {
                    if(ticks<12) return;var a=s.actor();String limb=limb(JOINTS[jointIndex]);
                    check(Math.abs(signature(a,"base/"+limb)-baseBefore)<.1 && Math.abs(signature(a,"flat/"+limb)-outerBefore)<.1,"Drag undo restores both meshes after rendering: "+JOINTS[jointIndex]);
                    if(++jointIndex<JOINTS.length) {stage=20;ticks=0;return;}
                    screenshot(mc,"01c-joint-editor.png");s.camera.x=3.5;s.camera.z=0;s.camera.yaw=90;s.move(new ActorTransform(.5,-60,.3,165,0));stage=6;ticks=0;
                }
            }
        } catch(Throwable e) {
            e.printStackTrace();try { Files.createDirectories(evidence);Files.writeString(evidence.resolve("acceptance.txt"),String.join("\n",results)+"\nFAIL stage "+stage+" "+e); } catch(Exception ignored) {}
            s.exit(mc.getConnection()!=null);mc.stop();stage=99;
        }
    }
    private void screenshot(Minecraft mc,String name) throws Exception {
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(evidence.resolve(name)); }
    }
    private String limb(String joint) {return joint.replace("elbow","arm").replace("knee","leg");}
    private double signature(ActorState actor,String channel) {
        Double value=actor.meshSignatures.get(channel);
        if(value==null && channel.startsWith("flat/")) value=actor.meshSignatures.get(channel.substring(5)); // SkinLayers3D may replace the flat overlay with its voxel mesh.
        if(value==null) throw new AssertionError("No actual mesh signature for "+channel+": "+actor.meshSignatures);return value;
    }
    private void press(Minecraft mc,String key) {
        String label=net.minecraft.network.chat.Component.translatable(key).getString();
        var button=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals(label)).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow();button.onPress();
    }
    private double matrixDifference(org.joml.Matrix4f a,org.joml.Matrix4f b) {
        float[] first=new float[16],second=new float[16];a.get(first);b.get(second);double result=0;for(int i=0;i<16;i++) result=Math.max(result,Math.abs(first[i]-second[i]));return result;
    }
    private void catalogPlace(Minecraft mc,String type) {
        var search=(net.minecraft.client.gui.components.EditBox)mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();
        search.setValue(type);mc.screen.mouseClicked(15,65,0);press(mc,"posestudio.catalog.place");
        check(mc.screen instanceof StudioScreen,"Catalog search and placement button: "+type);
    }
    private void testMouse(Minecraft mc,StudioState s) {
        StudioScreen screen=(StudioScreen)mc.screen;var c=s.camera;
        double cx=c.x,cy=c.y,cz=c.z,fov=c.fov;float yaw=c.yaw,pitch=c.pitch,roll=c.roll;
        ActorTransform actor=s.actor().transform;
        // The center near the feet can contain an actor/node with FA+Player enabled; use empty left viewport.
        double x=Math.min(152,mc.getWindow().getGuiScaledWidth()/4)+18,y=mc.getWindow().getGuiScaledHeight()-40;
        ((net.minecraft.client.gui.screens.Screen)screen).mouseScrolled(x,y,2);check(c.fov==fov-4,"Viewport wheel zoom");
        double zoomed=c.fov;((net.minecraft.client.gui.screens.Screen)screen).mouseScrolled(10,80,1);check(c.fov==zoomed,"Sidebar wheel does not zoom camera");
        ((net.minecraft.client.gui.screens.Screen)screen).mouseClicked(x,y,2);((net.minecraft.client.gui.screens.Screen)screen).mouseDragged(x,y+16,2,0,16);((net.minecraft.client.gui.screens.Screen)screen).mouseReleased(x,y+16,2);
        check(c.fov!=zoomed,"Middle button drag zoom");
        ((net.minecraft.client.gui.screens.Screen)screen).mouseClicked(x,y,0);((net.minecraft.client.gui.screens.Screen)screen).mouseDragged(x+20,y+8,0,20,8);((net.minecraft.client.gui.screens.Screen)screen).mouseReleased(x+20,y+8,0);
        check(c.yaw!=yaw && c.pitch!=pitch,"Empty viewport left drag turns camera");
        float turnedYaw=c.yaw,turnedPitch=c.pitch;
        ((net.minecraft.client.gui.screens.Screen)screen).mouseClicked(x,y,1);((net.minecraft.client.gui.screens.Screen)screen).mouseDragged(x+20,y+8,1,20,8);((net.minecraft.client.gui.screens.Screen)screen).mouseReleased(x+20,y+8,1);
        check(c.x!=cx || c.y!=cy || c.z!=cz,"Right button drag pans camera");
        check(c.yaw==turnedYaw && c.pitch==turnedPitch && c.roll==roll && actor.equals(s.actor().transform),"Mouse camera manipulation preserves actor and orientation during pan");
        s.undo.undo();check(c.x==cx && c.y==cy && c.z==cz && c.yaw==turnedYaw && c.pitch==turnedPitch,"Camera pan undo restores the whole gesture");
        c.zoom(1000);check(c.fov==10,"Mouse zoom lower FOV bound");c.zoom(-1000);check(c.fov==150,"Mouse zoom upper FOV bound");
        c.x=cx;c.y=cy;c.z=cz;c.yaw=yaw;c.pitch=pitch;c.roll=roll;c.fov=fov;screen.refresh();
    }
}
