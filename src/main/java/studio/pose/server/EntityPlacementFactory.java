package studio.pose.server;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;


public final class EntityPlacementFactory {
    public static Entity create(EntityType<?> type,ServerPlayer owner) {
        Entity entity=type.create(owner.serverLevel());
        if(entity instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().isEmpty()) item.setItem(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        if(entity==null || !inherits(entity,"me.xjqsh.lrtactical.entity.ThrowableItemEntity")) return entity;


        try {
            Class<?> manager=Class.forName("me.xjqsh.lrtactical.resource.CommonAssetsManager");
            Object provider=manager.getMethod("get").invoke(null);
            var indexes=(Collection<?>)provider.getClass().getMethod("getThrowableIndexes").invoke(provider);
            var ordered=new ArrayList<Object>(indexes);
            ordered.sort(Comparator.comparing(EntityPlacementFactory::indexId));
            for(Object index:ordered) {
                ItemStack stack=(ItemStack)index.getClass().getMethod("createItemStack").invoke(index);
                Entity candidate=(Entity)index.getClass().getMethod("createEntity",ItemStack.class,LivingEntity.class).invoke(index,stack,owner);
                if(candidate!=null && candidate.getType()==type) {
                    candidate.getClass().getMethod("setItem",ItemStack.class).invoke(candidate,stack.copy());return candidate;
                }
            }
        } catch(ReflectiveOperationException | LinkageError | RuntimeException ignored) {}
        return null;
    }
    private static String indexId(Object index) {try {return index.getClass().getMethod("getId").invoke(index).toString();}catch(ReflectiveOperationException ex) {return "";}}
    private static boolean inherits(Entity e,String name) {for(Class<?> type=e.getClass();type!=null;type=type.getSuperclass()) if(type.getName().equals(name)) return true;return false;}
    private EntityPlacementFactory() {}
}
