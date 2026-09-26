package studio.pose.server;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import studio.pose.PoseStudio;
import studio.pose.data.ActorTransform;

/** Server thread only. Every lock has an owner and an exact restoration snapshot. */
@Mod.EventBusSubscriber(modid=PoseStudio.ID)
public final class FreezeService {
    private static final Map<UUID, Lock> LOCKS = new HashMap<>();
    private static final Map<UUID,UUID> PLACED = new HashMap<>();
    private static final Set<UUID> SESSIONS = new HashSet<>();
    private record Snapshot(Vec3 position, float yaw, float pitch, Vec3 velocity, boolean gravity,
                            boolean ai, float body, float head) {}
    private static final class Lock {
        final UUID owner; final Entity entity; final Snapshot original; ActorTransform transform;
        Lock(ServerPlayer owner, Entity e) {
            this.owner=owner.getUUID(); entity=e;
            original=new Snapshot(e.position(),e.getYRot(),e.getXRot(),e.getDeltaMovement(),e.isNoGravity(),
                e instanceof Mob m && m.isNoAi(), e instanceof LivingEntity l?l.yBodyRot:0,e instanceof LivingEntity l?l.yHeadRot:0);
            transform=new ActorTransform(e.getX(),e.getY(),e.getZ(),e.getYRot(),e.getXRot());
        }
        void apply() {
            entity.setNoGravity(true); entity.setDeltaMovement(Vec3.ZERO);
            if(entity instanceof Mob m) m.setNoAi(true);
            set(entity,transform);
        }
        void restore() {
            if(entity.isRemoved()) return;
            set(entity,new ActorTransform(original.position.x,original.position.y,original.position.z,original.yaw,original.pitch));
            entity.setNoGravity(original.gravity); entity.setDeltaMovement(original.velocity);
            if(entity instanceof Mob m) m.setNoAi(original.ai);
            if(entity instanceof LivingEntity l) { l.yBodyRot=l.yBodyRotO=original.body; l.yHeadRot=l.yHeadRotO=original.head; }
            if(entity instanceof ServerPlayer p) p.connection.teleport(entity.getX(),entity.getY(),entity.getZ(),entity.getYRot(),entity.getXRot());
        }
    }
    private static boolean allowed(ServerPlayer p) {
        return p.hasPermissions(2) || p.server.isSingleplayerOwner(p.getGameProfile());
    }
    public static String begin(ServerPlayer p) {
        if(!allowed(p)) return "posestudio.error.permission";
        SESSIONS.add(p.getUUID());
        return "";
    }
    public static String enter(ServerPlayer p,java.util.List<studio.pose.network.StudioNetwork.Entry> entries) {
        if(!allowed(p)) return "posestudio.error.permission";
        if(entries.size()>studio.pose.StudioConfig.MAX_BATCH) return "posestudio.error.placement_limit";
        var pending=new LinkedHashMap<UUID,Lock>();var seen=new HashSet<UUID>();
        for(var entry:entries) {
            if(!seen.add(entry.actor())) return "posestudio.error.actor_unavailable";
            Entity e=p.serverLevel().getEntity(entry.actor());
            if(e==null || e.isRemoved() || e.distanceToSqr(p)>512*512) return "posestudio.error.actor_unavailable";
            if(e.isPassenger() || e.isVehicle()) return "posestudio.error.dismount";
            Lock existing=LOCKS.get(entry.actor());
            if(existing!=null) {if(!existing.owner.equals(p.getUUID())) return "posestudio.error.actor_locked";continue;}
            if(!validArea(p,entry.transform())) return "posestudio.error.transform_area";
            Lock lock=new Lock(p,e);lock.transform=entry.transform();pending.put(entry.actor(),lock);
        }
        if(exceeds(p.serverLevel(),pending.values().stream().map(lock->lock.entity).toList())) return "posestudio.error.placement_limit";
        try {for(var entry:pending.entrySet()) {LOCKS.put(entry.getKey(),entry.getValue());entry.getValue().apply();}}
        catch(RuntimeException ex) {for(var entry:pending.entrySet()) {LOCKS.remove(entry.getKey());entry.getValue().restore();}return "posestudio.error.actor_unavailable";}
        SESSIONS.add(p.getUUID());return "";
    }
    private static boolean validArea(ServerPlayer p,ActorTransform t) {
        return t.valid() && new Vec3(t.x(),t.y(),t.z()).distanceToSqr(p.position())<=512*512
            && p.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(t.x(),t.y(),t.z()));
    }
    public static String moveBatch(ServerPlayer p,java.util.List<studio.pose.network.StudioNetwork.Entry> entries) {
        if(!allowed(p) || !SESSIONS.contains(p.getUUID())) return "posestudio.error.session";
        if(entries.size()>studio.pose.StudioConfig.MAX_BATCH) return "posestudio.error.placement_limit";
        var before=new LinkedHashMap<Lock,ActorTransform>();
        for(var entry:entries) {
            Lock lock=LOCKS.get(entry.actor());
            if(lock==null || !lock.owner.equals(p.getUUID()) || lock.entity.isRemoved() || lock.entity.level()!=p.level()) return "posestudio.error.actor_owner";
            if(before.containsKey(lock) || !validArea(p,entry.transform())) return "posestudio.error.transform_area";
            before.put(lock,lock.transform);
        }
        try {for(var entry:entries) {Lock lock=LOCKS.get(entry.actor());lock.transform=entry.transform();lock.apply();}}
        catch(RuntimeException ex) {before.forEach((lock,t)->{lock.transform=t;lock.apply();});return "posestudio.error.transform_area";}
        for(Lock lock:before.keySet()) if(lock.entity instanceof ServerPlayer player) player.connection.teleport(lock.transform.x(),lock.transform.y(),lock.transform.z(),lock.transform.yaw(),lock.transform.pitch());
        return "";
    }
    public static String acquire(ServerPlayer p, UUID id) {
        if(!SESSIONS.contains(p.getUUID()) || !allowed(p)) return "posestudio.error.session";
        Entity e=p.serverLevel().getEntity(id);
        if(e==null || e.isRemoved() || e.distanceToSqr(p)>512*512) return "posestudio.error.actor_unavailable";
        if(e.isPassenger() || e.isVehicle()) return "posestudio.error.dismount";
        Lock existing=LOCKS.get(id);
        if(existing!=null) return existing.owner.equals(p.getUUID())?"":"posestudio.error.actor_locked";
        if(exceeds(p.serverLevel(),List.of(e))) return "posestudio.error.placement_limit";
        Lock lock=new Lock(p,e); LOCKS.put(id,lock); lock.apply(); return "";
    }
    public static String move(ServerPlayer p, UUID id, ActorTransform t) {
        Lock l=LOCKS.get(id);
        if(!allowed(p) || l==null || !l.owner.equals(p.getUUID())) return "posestudio.error.actor_owner";
        if(!t.valid() || new Vec3(t.x(),t.y(),t.z()).distanceToSqr(p.position())>512*512 || !p.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(t.x(),t.y(),t.z()))) return "posestudio.error.transform_area";
        l.transform=t; l.apply();
        if(l.entity instanceof ServerPlayer player) player.connection.teleport(t.x(),t.y(),t.z(),t.yaw(),t.pitch());
        return "";
    }
    public static void release(ServerPlayer p, UUID id) {
        Lock l=LOCKS.get(id); if(l!=null && l.owner.equals(p.getUUID())) { LOCKS.remove(id); if(PLACED.remove(id)!=null) l.entity.discard(); else l.restore(); }
    }
    public static void end(UUID owner) {
        SESSIONS.remove(owner);
        Iterator<Lock> i=LOCKS.values().iterator();
        while(i.hasNext()) { Lock l=i.next(); if(l.owner.equals(owner)) { i.remove(); if(PLACED.remove(l.entity.getUUID())!=null) l.entity.discard(); else l.restore(); } }
    }
    public record Placement(UUID actor, String error) {}
    public static Placement place(ServerPlayer p, net.minecraft.resources.ResourceLocation type, ActorTransform t) {
        UUID empty=new UUID(0,0);
        if(!SESSIONS.contains(p.getUUID()) || !allowed(p)) return new Placement(empty,"posestudio.error.session");
        if(!t.valid() || new Vec3(t.x(),t.y(),t.z()).distanceToSqr(p.position())>512*512
            || !p.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(t.x(),t.y(),t.z())))
            return new Placement(empty,"posestudio.error.transform_area");

        var registry=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
        if(!registry.containsKey(type)) return new Placement(empty,"posestudio.error.create_entity");
        Entity e=null;
        try {
            e=EntityPlacementFactory.create(registry.get(type),p);
            if(e==null || e instanceof net.minecraft.world.entity.player.Player) return new Placement(empty,"posestudio.error.create_entity");
            if(exceeds(p.serverLevel(),List.of(e))) {e.discard();return new Placement(empty,"posestudio.error.placement_limit");}
            e.addTag("posestudio:temporary_actor");set(e,t); if(e instanceof Mob m) m.setPersistenceRequired();
            Lock lock=new Lock(p,e);lock.apply();LOCKS.put(e.getUUID(),lock);PLACED.put(e.getUUID(),p.getUUID());
            if(!p.serverLevel().addFreshEntity(e)) { LOCKS.remove(e.getUUID());PLACED.remove(e.getUUID());e.discard();return new Placement(empty,"posestudio.error.create_entity"); }
            return new Placement(e.getUUID(),"");
        } catch(RuntimeException ex) {
            if(e!=null) {LOCKS.remove(e.getUUID());PLACED.remove(e.getUUID());e.discard();}
            return new Placement(empty,"posestudio.error.create_entity");
        }
    }
    public static String removePlaced(ServerPlayer p, UUID id) {
        if(!allowed(p) || !p.getUUID().equals(PLACED.get(id))) return "posestudio.error.not_placed";
        release(p,id);return "";
    }
    public static long activeCount(net.minecraft.server.level.ServerLevel level) {return LOCKS.values().stream().filter(lock->lock.entity.level()==level && !lock.entity.isRemoved()).count();}
    public static long propCount(net.minecraft.server.level.ServerLevel level) {return LOCKS.values().stream().filter(lock->lock.entity.level()==level && !lock.entity.isRemoved() && studio.pose.StudioConfig.prop(lock.entity)).count();}
    private static boolean exceeds(net.minecraft.server.level.ServerLevel level,List<Entity> added) {
        long props=propCount(level),living=activeCount(level)-props;
        for(Entity entity:added) {if(studio.pose.StudioConfig.prop(entity)) props++;else living++;}
        return living>studio.pose.StudioConfig.maxActors() || props>studio.pose.StudioConfig.maxProps();
    }
    public static boolean frozen(Entity e) { return LOCKS.containsKey(e.getUUID()); }
    public static ActorTransform transform(ServerPlayer owner,UUID actor) {
        Lock lock=LOCKS.get(actor);
        return lock!=null && lock.owner.equals(owner.getUUID())?lock.transform:null;
    }
    private static void set(Entity e, ActorTransform t) {
        e.moveTo(t.x(),t.y(),t.z(),t.yaw(),t.pitch());
        e.xo=e.xOld=t.x(); e.yo=e.yOld=t.y(); e.zo=e.zOld=t.z(); e.yRotO=t.yaw(); e.xRotO=t.pitch();
        if(e instanceof LivingEntity l) { l.yBodyRot=l.yBodyRotO=t.yaw(); l.yHeadRot=l.yHeadRotO=t.yaw(); }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        for(Lock l:new ArrayList<>(LOCKS.values())) {
            ServerPlayer owner=l.entity.getServer().getPlayerList().getPlayer(l.owner);
            if(owner==null || owner.level()!=l.entity.level() || l.entity.isRemoved() || !allowed(owner)) {
                LOCKS.remove(l.entity.getUUID()); if(PLACED.remove(l.entity.getUUID())!=null) l.entity.discard(); else l.restore();
            } else l.apply();
        }
    }
    @SubscribeEvent public static void loaded(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        // A crash can save a temporary actor before logout cleanup. Do not keep that orphan on reload.
        if(!event.getLevel().isClientSide && event.getEntity().getTags().contains("posestudio:temporary_actor")
            && !LOCKS.containsKey(event.getEntity().getUUID())) event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { end(e.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { end(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppingEvent e) {
        for(Lock l:new ArrayList<>(LOCKS.values())) { if(PLACED.containsKey(l.entity.getUUID())) l.entity.discard(); else l.restore(); }
        LOCKS.clear(); SESSIONS.clear(); PLACED.clear();
    }
}
