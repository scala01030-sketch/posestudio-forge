package studio.pose.verification;

import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import studio.pose.client.*;
import studio.pose.data.*;
import studio.pose.network.StudioNetwork;
import studio.pose.server.FreezeService;
import studio.pose.ui.*;

/** Actual installed pack catalogs and equipment. No user world or user inventory is opened. */
public final class ItemCatalogRun {
    private int stage,ticks,index;
    private final long start=System.nanoTime();
    private final Path evidence=Path.of(System.getProperty("posestudio.acceptance.evidence"));
    private final List<String> checks=new ArrayList<>();
    private List<SceneCatalog.Entry> items,examples;
    private ItemStack main,off;
    private UUID gun,npc,placed;
    private ActorTransform before;
    private Map<String,BonePose> pose;
    private List<UUID> detached=List.of();
    private org.joml.Matrix4f standingHead,standingArm;
    private CompoundTag carryBefore;
    private Object genderBefore,genderTest;
    private org.joml.Matrix4f genderMatrix;
    private float genderBounce;
    private void next(int s) {stage=s;ticks=0;}
    private void check(boolean success,String label) {if(!success) throw new AssertionError(label);checks.add("PASS "+label);System.out.println("POSE_ITEMS "+checks.get(checks.size()-1));}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;Minecraft mc=Minecraft.getInstance();StudioState s=StudioState.INSTANCE;ticks++;
        try {
            if(System.nanoTime()-start>600_000_000_000L) throw new AssertionError("Items timeout stage "+stage+" "+s.message);
            switch(stage) {
                case 0 -> {
                    if(mc.screen==null || mc.getOverlay()!=null || ticks<20) return;Files.createDirectories(evidence);
                    mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);
                    mc.createWorldOpenFlows().createFreshLevel("PoseItems-"+System.currentTimeMillis(),new LevelSettings("PoseItems",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());next(1);
                }
                case 1 -> {
                    if(mc.player==null || mc.screen!=null || ticks<30) return;
                    items=SceneCatalog.items();check(!items.isEmpty(),"Native item catalog is populated");
                    var ids=new HashSet<net.minecraft.resources.ResourceLocation>();for(var e:items) ids.add(BuiltInRegistries.ITEM.getKey(e.source().stack().getItem()));
                    var missing=new ArrayList<String>();for(Item item:BuiltInRegistries.ITEM) if(item!=Items.AIR && !ids.contains(BuiltInRegistries.ITEM.getKey(item))) missing.add(BuiltInRegistries.ITEM.getKey(item).toString());
                    check(missing.isEmpty(),"All registered items are represented: "+ids.size()+" IDs; missing="+missing);
                    check(SceneCatalog.entities().size()==BuiltInRegistries.ENTITY_TYPE.size(),"All registered entity types remain listed");
                    var mods=new LinkedHashMap<String,String>();for(var mod:net.minecraftforge.fml.ModList.get().getMods()) mods.put(mod.getModId(),mod.getVersion().toString());
                    Files.writeString(evidence.resolve("player-model-mods.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(mods));
                    for(String id:List.of("skinlayers3d","entity_model_features","entity_texture_features","physicsmod","tacz","tacztweaks","carryon","wildfire_gender","femaleplasticsurgery","customskinloader","simplebedrockmodel")) check(mods.containsKey(id),"Model compatibility test has loaded mod: "+id);
                    var guns=items.stream().filter(e->e.source().stack().hasTag() && e.source().stack().getTag().contains("GunId")).toList();
                    check(guns.size()>=3,"TaCZ native creative variants include at least three gun IDs");
                    main=guns.stream().filter(e->e.matches("tacz:m4a1")).findFirst().orElse(guns.get(0)).source().stack();
                    main.getOrCreateTag().putString("PoseFixtureMarker","full-stack-preservation");
                    off=new ItemStack(Items.DIAMOND_SWORD);off.setHoverName(net.minecraft.network.chat.Component.literal("Fixture副手剑"));off.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS,3);
                    var tactical=items.stream().filter(e->e.matches("lrtactical:") && e.source().stack().hasTag()).findFirst().orElseThrow();
                    examples=List.of(guns.get(1),guns.get(2),tactical,items.stream().filter(e->e.source().stack().is(Items.APPLE)).findFirst().orElseThrow());
                    Files.write(evidence.resolve("item-catalog.txt"),items.stream().map(e->e.label().getString()+" | "+e.detail()+" | "+e.source().stack().save(new CompoundTag())).toList());
                    mc.player.getInventory().selected=2;
                    mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.connection.teleport(0,-60,0,180,0);p.serverLevel().setDayTime(6000);p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,mc.getSingleplayerServer());var initialEntities=new ArrayList<Entity>();p.serverLevel().getAllEntities().forEach(initialEntities::add);for(Entity e:initialEntities) if(e!=null && e!=p) e.discard();p.getInventory().selected=2;p.setItemSlot(EquipmentSlot.MAINHAND,main.copy());p.setItemSlot(EquipmentSlot.OFFHAND,off.copy());p.inventoryMenu.broadcastChanges();}).join();next(2);
                }
                case 2 -> {if(ticks<25 || !same(mc.player.getMainHandItem(),main) || !same(mc.player.getOffhandItem(),off)) return;check(SceneCatalog.hands().size()==0,"No actor selected before Studio");s.toggle();next(3);}
                case 3 -> {
                    if(!s.active || ticks<35 || s.placed.size()!=2 || s.placed.stream().anyMatch(id->s.entity(id)==null)) return;
                    check(mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().isEmpty(),"Player equipment clears after successful atomic freeze");
                    detached=List.copyOf(s.placed);gun=detached.stream().filter(id->same(((ItemEntity)s.entity(id)).getItem(),main)).findFirst().orElseThrow();
                    check(detached.stream().anyMatch(id->same(((ItemEntity)s.entity(id)).getItem(),off)),"Vanilla named enchanted off-hand stack separates with all data");
                    check(detached.stream().allMatch(id->s.actors.get(id).frozen),"Both detached items are frozen independent scene actors");
                    check(!ItemLabels.name(((ItemEntity)s.entity(gun)).getItem()).getString().startsWith("item.tacz."),"TaCZ native localized gun name resolves in the editor");
                    check(s.actors.size()==3,"Player and two held items count as three actors");
                    check(s.entity(gun).position().distanceTo(mc.player.position())>.8,"Gun is placed beside the actor");
                    s.camera.x=0;s.camera.y=-58.8;s.camera.z=-5;s.camera.yaw=0;s.camera.pitch=0;s.camera.fov=65;
                    next(4);
                }
                case 4 -> {
                    if(ticks<25) return;var actor=s.actors.get(mc.player.getUUID());
                    check(actor.bones.containsKey("right_arm") && actor.bones.containsKey("left_arm"),"Neutral player limbs are captured from actual rendering");
                    var adapter=studio.pose.model.RenderContext.inspect(mc.player,actor);
                    for(var e:adapter.bindings.entrySet()) if(List.of("left_arm","right_arm","left_leg","right_leg","body","head").contains(e.getValue().name())) {
                        var rest=e.getKey().getInitialPose();var p=actor.bones.get(e.getValue().name());
                        check(p!=null && Math.abs(p.rotation[0]-Math.toDegrees(rest.xRot))<.001 && Math.abs(p.rotation[1]-Math.toDegrees(rest.yRot))<.001 && Math.abs(p.rotation[2]-Math.toDegrees(rest.zRot))<.001,"Rest stance replaces held weapon pose: "+e.getValue().name());
                    }
                    var screen=(StudioScreen)mc.screen;screen.rescan();check(detached.stream().allMatch(id->screen.listedEntities().stream().anyMatch(e->e.getUUID().equals(id))),"Detached vanilla and TaCZ items appear in actor edit list");
                    screenshot(mc,"01-neutral-player-separated-items.png");standingHead=new org.joml.Matrix4f(actor.nodes.get("head").matrix());standingArm=new org.joml.Matrix4f(actor.nodes.get("right_arm").matrix());swim(mc,true);next(40);
                }
                case 40 -> {
                    swim(mc,true);if(ticks<15) return;var actor=s.actors.get(mc.player.getUUID());
                    check(difference(standingHead,actor.nodes.get("head").matrix())<.001,"Swimming or crawl interpolation cannot tilt frozen standing actor root");
                    check(mc.player.getPose()==Pose.SWIMMING && mc.player.getSwimAmount(1)==1,"Neutral rendering leaves saved native swimming state unchanged outside rendering");
                    swim(mc,false);next(41);
                }
                case 41 -> {
                    if(ticks<10) return;carryBefore=carryTag(mc);carryChest(mc);check(carryActive(mc),"CarryOn native carrying pose is active during compatibility test");next(42);
                }
                case 42 -> {
                    if(ticks<15) return;var actor=s.actors.get(mc.player.getUUID());
                    check(difference(standingHead,actor.nodes.get("head").matrix())<.001,"CarryOn leaves the neutral frozen body root standing");
                    check(difference(standingArm,actor.nodes.get("right_arm").matrix())<.001,"CarryOn holding animation cannot replace frozen arm pose");
                    actor.bones.get("right_arm").rotation[0]=35;next(43);
                }
                case 43 -> {
                    if(ticks<15) return;var actor=s.actors.get(mc.player.getUUID());
                    check(actor.bones.get("right_arm").rotation[0]==35 && difference(standingArm,actor.nodes.get("right_arm").matrix())>.1,"Authored arm adjustment renders while CarryOn native holding pose is active");
                    screenshot(mc,"01b-carryon-edited-arm.png");actor.bones.get("right_arm").rotation[0]=0;restoreCarry(mc,carryBefore);check(!carryActive(mc),"Compatibility fixture restores original empty CarryOn data");
                    genderBefore=genderMap().get(mc.player.getUUID());Class<?> type=Class.forName("com.wildfire.main.GenderPlayer"),kind=Class.forName("com.wildfire.main.GenderPlayer$Gender");
                    genderTest=type.getConstructor(UUID.class,kind).newInstance(mc.player.getUUID(),kind.getField("FEMALE").get(null));type.getMethod("updateBustSize",float.class).invoke(genderTest,.6f);type.getMethod("updateBreastPhysics",boolean.class).invoke(genderTest,true);genderMap().put(mc.player.getUUID(),genderTest);
                    for(String getter:List.of("getLeftBreastPhysics","getRightBreastPhysics")) {var physics=type.getMethod(getter).invoke(genderTest);for(String name:List.of("breastSize","preBreastSize")) {var field=physics.getClass().getDeclaredField(name);field.setAccessible(true);field.setFloat(physics,.6f);}}
                    GenderProbe.matrix=null;GenderProbe.calls=GenderProbe.layerCalls=0;next(44);
                }
                case 44 -> {
                    if(ticks<20) return;check(GenderProbe.calls>0 && GenderProbe.matrix!=null,"Wildfire Gender custom geometry renders with FemalePlasticSurgery loaded; layers="+GenderProbe.layerCalls+" boxes="+GenderProbe.calls+" sameData="+(genderMap().get(mc.player.getUUID())==genderTest));
                    genderMatrix=new org.joml.Matrix4f(GenderProbe.matrix);genderBounce=genderBounce();s.actors.get(mc.player.getUUID()).bones.get("body").rotation[0]=30;next(45);
                }
                case 45 -> {
                    updateGenderPhysics(mc);if(ticks<20) return;
                    check(difference(genderMatrix,GenderProbe.matrix)>.1,"Wildfire custom body layer follows authored torso rotation");
                    check(genderBounce()==genderBounce,"Frozen Wildfire layer physics does not continue updating");genderMatrix=new org.joml.Matrix4f(GenderProbe.matrix);next(46);
                }
                case 46 -> {
                    updateGenderPhysics(mc);if(ticks<20) return;
                    check(difference(genderMatrix,GenderProbe.matrix)<.001,"Wildfire custom geometry stays fixed across subsequent render frames; difference="+difference(genderMatrix,GenderProbe.matrix));
                    screenshot(mc,"01c-wildfire-body-pose.png");s.actors.get(mc.player.getUUID()).bones.get("body").rotation[0]=0;if(genderBefore==null) genderMap().remove(mc.player.getUUID());else genderMap().put(mc.player.getUUID(),genderBefore);
                    var actor=s.actors.get(mc.player.getUUID());
                    s.select(s.entity(gun));s.setMode(StudioState.Mode.ACTOR);before=s.actor().transform;pose=Map.copyOf(actor.bones);mc.setScreen(new StudioScreen());
                    var fields=fields(mc);fields.get(0).setValue(Double.toString(before.x()+1));fields.get(1).setValue(Double.toString(before.y()+.4));fields.get(3).setValue("30");fields.get(4).setValue("25");fields.get(5).setValue("45");press(mc,"posestudio.ui.apply");next(5);
                }
                case 5 -> {
                    if(ticks<20) return;check(Math.abs(s.actor().transform.x()-before.x()-1)<.00001 && Math.abs(s.actor().transform.y()-before.y()-.4)<.00001 && s.actor().transform.roll()==45,"TaCZ detached gun accepts independent position and free rotation: before="+before+" after="+s.actor().transform+" message="+s.message.getString());
                    check(s.actor().lastMainRenderNanos>0,"TaCZ detached gun uses native main-frame renderer");check(s.actors.get(mc.player.getUUID()).bones.equals(pose),"Moving gun preserves player pose");screenshot(mc,"02-independent-tacz-gun.png");s.undo.undo();next(6);
                }
                case 6 -> {if(ticks<20) return;check(s.actor().transform.equals(before),"Detached gun movement and rotation undo");s.select(mc.player);s.freeze();next(7);}
                case 7 -> {
                    if(ticks<25) return;check(!s.actor().frozen && s.placed.isEmpty(),"Restoring actor retires both detached display copies");
                    check(same(mc.player.getMainHandItem(),main) && same(mc.player.getOffhandItem(),off),"Restore returns full TaCZ and vanilla stacks to original equipment slots");
                    check(detached.stream().allMatch(id->s.entity(id)==null && !s.actors.containsKey(id)),"Detached entities and editor rows are removed together");
                    s.freeze();next(8);
                }
                case 8 -> {
                    if(ticks<25 || s.placed.size()!=2) return;check(s.actor().frozen && mc.player.getMainHandItem().isEmpty(),"Refreeze separates equipment again without duplicate originals");
                    var npcId=mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var e=EntityType.SKELETON.create(p.serverLevel());e.moveTo(2,-60,0,180,0);e.setInvulnerable(true);e.setItemSlot(EquipmentSlot.MAINHAND,main.copy());p.serverLevel().addFreshEntity(e);return e.getUUID();}).join();npc=npcId;next(9);
                }
                case 9 -> {if(ticks<25 || s.entity(npc)==null) return;s.select(s.entity(npc));s.freeze();next(10);}
                case 10 -> {
                    if(ticks<25 || !s.actor().frozen) return;check(((LivingEntity)s.entity(npc)).getMainHandItem().isEmpty() && s.placed.size()==3,"Freezing another humanoid also separates its equipment; stack="+((LivingEntity)s.entity(npc)).getMainHandItem()+" props="+s.placed.size());
                    check(s.actors.size()==5,"Player, skeleton and three detached items are counted independently");s.select(mc.player);press(mc,"posestudio.ui.scene");press(mc,"posestudio.catalog.items");var catalog=(EntityCatalogScreen)mc.screen;
                    check(catalog.listedEntries().size()==items.size(),"Item tab exposes the complete loaded native stack catalog");fields(mc).get(0).setValue("tacz:m4a1");check(!catalog.listedEntries().isEmpty() && catalog.listedEntries().stream().allMatch(e->e.matches("tacz:m4a1")),"Search finds TaCZ GunId variants");
                    next(11);
                }
                case 11 -> {
                    if(ticks<5) return;if(index==0) screenshot(mc,"03-tacz-item-search.png");if(index>=examples.size()) {mc.screen.onClose();s.capture();next(14);return;}
                    if(!(mc.screen instanceof EntityCatalogScreen)) {press(mc,"posestudio.ui.scene");press(mc,"posestudio.catalog.items");}
                    var catalog=(EntityCatalogScreen)mc.screen;ItemStack expected=examples.get(index).source().stack();
                    String query=expected.hasTag() && expected.getTag().contains("GunId")?expected.getTag().getString("GunId"):expected.hasTag() && expected.getTag().contains("ThrowableId")?expected.getTag().getString("ThrowableId"):BuiltInRegistries.ITEM.getKey(expected.getItem()).toString();
                    fields(mc).get(0).setValue(query);check(catalog.listedEntries().stream().anyMatch(e->same(e.source().stack(),expected)),"Search exposes native stack variant "+query);
                    int row=0;while(row<catalog.listedEntries().size() && !same(catalog.listedEntries().get(row).source().stack(),expected)) row++;
                    int visible=Math.max(1,(mc.screen.height-95)/28),scroll=Math.min(row,Math.max(0,catalog.listedEntries().size()-visible));
                    for(int i=0;i<scroll;i++) mc.screen.mouseScrolled(15,65,-1);mc.screen.mouseClicked(15,65+(row-scroll)*28,0);
                    var f=fields(mc);f.get(1).setValue(Double.toString(-2+index*.9));f.get(2).setValue("-59");f.get(3).setValue("0");press(mc,"posestudio.catalog.place");next(12);
                }
                case 12 -> {
                    if(ticks<25 || s.actor()==null || !s.placed.contains(s.actor().id) || !(s.entity(s.actor().id) instanceof ItemEntity)) return;
                    placed=s.actor().id;check(same(((ItemEntity)s.entity(placed)).getItem(),examples.get(index).source().stack()),"Full stack data survives native packet and server placement: "+index);
                    check(s.actor().frozen && s.actor().lastMainRenderNanos>0,"Placed item is frozen and rendered: "+index);screenshot(mc,"04-variant-"+index+".png");index++;next(11);
                }
                case 14 -> {if(ticks<25) return;check(mc.screen==null && s.capture && s.actors.values().stream().allMatch(a->a.frozen),"Capture preserves separated actor scene without UI");screenshot(mc,"05-items-capture.png");s.exit(true);next(15);}
                case 15 -> {
                    if(ticks<25) return;check(same(mc.player.getInventory().getItem(2),main) && same(mc.player.getOffhandItem(),off),"Exit restores original hotbar index and off hand");
                    mc.getSingleplayerServer().submit(()->{
                        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);check(FreezeService.activeCount(p.serverLevel())==0,"Exit releases all locks");check(!p.getPersistentData().contains("posestudio:equipment"),"Normal exit clears durable equipment snapshot");
                        var e=(LivingEntity)p.serverLevel().getEntity(npc);check(e!=null && same(e.getMainHandItem(),main),"NPC original equipment is restored");
                        studio.pose.StudioConfig.MAX_PROPS.set(20);FreezeService.begin(p);
                        for(int i=0;i<19;i++) {var result=FreezeService.place(p,PlacementSpec.item(new ItemStack(Items.APPLE)),new ActorTransform(0,-59,2,0,0));if(!result.error().isEmpty()) throw new AssertionError(result.error());}
                        check(!FreezeService.acquire(p,p.getUUID()).isEmpty(),"Nineteen existing props plus two hands reject freeze at twenty-prop cap");
                        check(!FreezeService.frozen(p) && same(p.getMainHandItem(),main) && same(p.getOffhandItem(),off),"Rejected freeze preserves actor and both original stacks");
                        check(FreezeService.propCount(p.serverLevel())==19 && !p.getPersistentData().contains("posestudio:equipment"),"Rejected freeze has no extra props or recovery mutation");
                        FreezeService.end(p.getUUID());studio.pose.StudioConfig.MAX_PROPS.set(25);
                        CompoundTag recovery=new CompoundTag();recovery.putInt("slot",2);recovery.put("main",main.save(new CompoundTag()));recovery.put("off",off.save(new CompoundTag()));p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);p.getPersistentData().put("posestudio:equipment",recovery);
                        FreezeService.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));check(same(p.getInventory().getItem(2),main) && same(p.getOffhandItem(),off),"Recovery restores serialized equipment after interrupted session");
                    }).join();Files.write(evidence.resolve("acceptance.txt"),checks);System.out.println("POSE_ITEMS COMPLETE");mc.stop();stage=99;
                }
            }
        } catch(Throwable error) {error.printStackTrace();try {Files.writeString(evidence.resolve("acceptance.txt"),String.join("\n",checks)+"\nFAIL stage "+stage+" "+error);}catch(Exception ignored){}s.exit(true);studio.pose.StudioConfig.MAX_PROPS.set(25);mc.stop();stage=99;}
    }
    private static boolean same(ItemStack a,ItemStack b) {return a.getCount()==b.getCount() && a.save(new CompoundTag()).equals(b.save(new CompoundTag()));}
    private static double difference(org.joml.Matrix4f a,org.joml.Matrix4f b) {float[] x=new float[16],y=new float[16];a.get(x);b.get(y);double d=0;for(int i=0;i<16;i++) d=Math.max(d,Math.abs(x[i]-y[i]));return d;}
    private static void swim(Minecraft mc,boolean enabled) {mc.player.setPose(enabled?Pose.SWIMMING:Pose.STANDING);var access=(studio.pose.verification.mixin.LivingPoseAccess)(Object)mc.player;access.posefixtures$swim(enabled?1:0);access.posefixtures$oldSwim(enabled?1:0);}
    private static Object carry(Minecraft mc) throws Exception {return Class.forName("tschipp.carryon.common.carry.CarryOnDataManager").getMethod("getCarryData",net.minecraft.world.entity.player.Player.class).invoke(null,mc.player);}
    private static CompoundTag carryTag(Minecraft mc) throws Exception {var data=carry(mc);return ((CompoundTag)data.getClass().getMethod("getNbt").invoke(data)).copy();}
    private static boolean carryActive(Minecraft mc) throws Exception {var data=carry(mc);return (boolean)data.getClass().getMethod("isCarrying").invoke(data);}
    private static void restoreCarry(Minecraft mc,CompoundTag tag) throws Exception {Class<?> data=Class.forName("tschipp.carryon.common.carry.CarryOnData");var value=data.getConstructor(CompoundTag.class).newInstance(tag.copy());Class.forName("tschipp.carryon.common.carry.CarryOnDataManager").getMethod("setCarryData",net.minecraft.world.entity.player.Player.class,data).invoke(null,mc.player,value);}
    private static void carryChest(Minecraft mc) throws Exception {var data=carry(mc);var block=net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState();data.getClass().getMethod("setBlock",net.minecraft.world.level.block.state.BlockState.class,net.minecraft.world.level.block.entity.BlockEntity.class).invoke(data,block,new net.minecraft.world.level.block.entity.ChestBlockEntity(net.minecraft.core.BlockPos.ZERO,block));restoreCarry(mc,((CompoundTag)data.getClass().getMethod("getNbt").invoke(data)).copy());}
    @SuppressWarnings("unchecked") private static Map<UUID,Object> genderMap() throws Exception {return (Map<UUID,Object>)Class.forName("com.wildfire.main.WildfireGender").getField("CLOTHING_PLAYERS").get(null);}
    private Object genderPhysics() throws Exception {return genderTest.getClass().getMethod("getLeftBreastPhysics").invoke(genderTest);}
    private float genderBounce() throws Exception {var physics=genderPhysics();return (float)physics.getClass().getMethod("getBounceY").invoke(physics);}
    private void updateGenderPhysics(Minecraft mc) throws Exception {var physics=genderPhysics();var armor=Class.forName("com.wildfire.render.armor.EmptyGenderArmor").getField("INSTANCE").get(null);physics.getClass().getMethod("update",net.minecraft.world.entity.player.Player.class,Class.forName("com.wildfire.api.IGenderArmor")).invoke(physics,mc.player,armor);}
    private static List<EditBox> fields(Minecraft mc) {return mc.screen.children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).toList();}
    private static void press(Minecraft mc,String key) {String label=net.minecraft.client.resources.language.I18n.get(key);((Button)mc.screen.children().stream().filter(w->w instanceof Button b && b.getMessage().getString().equals(label)).findFirst().orElseThrow()).onPress();}
    private void screenshot(Minecraft mc,String name) throws Exception {try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(evidence.resolve(name));}}
}
