package studio.pose.client;

import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;


public final class ItemLabels {
    private static boolean resolved;
    private static Method gunIndex;
    public static Component name(ItemStack stack) {
        if(!stack.hasCustomHoverName() && stack.hasTag() && stack.getTag().contains("GunId")) {
            try {
                if(!resolved) {resolved=true;gunIndex=Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getClientGunIndex",ResourceLocation.class);}
                ResourceLocation id=ResourceLocation.tryParse(stack.getTag().getString("GunId"));
                if(gunIndex!=null && id!=null && gunIndex.invoke(null,id) instanceof Optional<?> index && index.isPresent()) {
                    Object text=index.get().getClass().getMethod("getName").invoke(index.get());
                    if(text instanceof String key && !key.isBlank()) return Component.translatable(key);
                }
            } catch(ReflectiveOperationException | LinkageError | RuntimeException ignored) {}
        }
        try {return stack.getHoverName();}catch(RuntimeException | LinkageError ignored) {return Component.literal(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());}
    }
    private ItemLabels() {}
}
