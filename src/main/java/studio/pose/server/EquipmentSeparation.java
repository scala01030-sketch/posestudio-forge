package studio.pose.server;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import studio.pose.data.ActorTransform;


final class EquipmentSeparation {
    private static final String RECOVERY="posestudio:equipment";
    final LivingEntity actor;
    final List<ItemEntity> props=new ArrayList<>();
    private final CompoundTag snapshot=new CompoundTag();
    private boolean detached;
    EquipmentSeparation(LivingEntity actor,ActorTransform transform) {
        this.actor=actor;
        snapshot.putInt("slot",actor instanceof Player p?p.getInventory().selected:-1);
        snapshot.put("main",actor.getMainHandItem().save(new CompoundTag()));
        snapshot.put("off",actor.getOffhandItem().save(new CompoundTag()));
        for(boolean off:new boolean[]{false,true}) {
            ItemStack original=off?actor.getOffhandItem():actor.getMainHandItem();if(original.isEmpty()) continue;
            ItemStack display=original.copy();display.setCount(1);
            double side=off?-1.2:1.2,yaw=Math.toRadians(transform.yaw());
            var prop=new ItemEntity(actor.level(),transform.x()+Math.cos(yaw)*side,transform.y()+Math.max(.6,actor.getBbHeight()*.55),transform.z()+Math.sin(yaw)*side,display);
            prop.setPickUpDelay(Integer.MAX_VALUE);prop.setUnlimitedLifetime();props.add(prop);
        }
    }
    void detach() {
        if(detached || props.isEmpty()) return;
        actor.getPersistentData().put(RECOVERY,snapshot.copy());
        actor.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);actor.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);detached=true;sync(actor);
    }
    void restore() {if(detached) {recover(actor);detached=false;}}
    static void recover(LivingEntity actor) {
        if(!actor.getPersistentData().contains(RECOVERY)) return;
        CompoundTag saved=actor.getPersistentData().getCompound(RECOVERY);
        restoreSlot(actor,EquipmentSlot.MAINHAND,ItemStack.of(saved.getCompound("main")),saved.getInt("slot"));
        restoreSlot(actor,EquipmentSlot.OFFHAND,ItemStack.of(saved.getCompound("off")),40);
        actor.getPersistentData().remove(RECOVERY);
        sync(actor);
    }
    private static void sync(LivingEntity actor) {

        if(actor.level() instanceof net.minecraft.server.level.ServerLevel level) {
            var equipment=List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND,actor.getMainHandItem().copy()),com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND,actor.getOffhandItem().copy()));
            level.getChunkSource().broadcastAndSend(actor,new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(actor.getId(),equipment));
            if(actor instanceof net.minecraft.server.level.ServerPlayer player) player.inventoryMenu.broadcastChanges();
        }
    }
    private static void restoreSlot(LivingEntity actor,EquipmentSlot slot,ItemStack stack,int index) {
        if(stack.isEmpty()) return;
        if(actor instanceof Player p) {

            if(index<0 || index>=p.getInventory().getContainerSize()) return;
            if(p.getInventory().getItem(index).isEmpty()) p.getInventory().setItem(index,stack);
            else if(!p.getInventory().add(stack) && !stack.isEmpty()) p.drop(stack,false);
            p.inventoryMenu.broadcastChanges();
        } else actor.setItemSlot(slot,stack);
    }
}
