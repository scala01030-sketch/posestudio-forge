package studio.pose.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import studio.pose.data.PlacementSpec;

/** Read native registries and creative stack variants once per opening, never once per keystroke. */
public final class SceneCatalog {
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    public record Entry(PlacementSpec source,Component label,String detail,String search) {
        public boolean matches(String query) {return search.contains(query.strip().toLowerCase(Locale.ROOT));}
    }
    public static List<Entry> entities() {
        return BuiltInRegistries.ENTITY_TYPE.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().location().toString()))
            .map(e->entry(PlacementSpec.entity(e.getKey().location()),e.getValue().getDescription(),e.getKey().location().toString(),"" )).toList();
    }
    public static List<Entry> items() {
        Minecraft mc=Minecraft.getInstance();var variants=new LinkedHashMap<String,ItemStack>();
        try {
            if(mc.level!=null) CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(),mc.player!=null && mc.player.hasPermissions(2),mc.level.registryAccess());
            for(CreativeModeTab tab:CreativeModeTabs.allTabs()) {
                try {for(ItemStack stack:tab.getSearchTabDisplayItems()) add(variants,stack);for(ItemStack stack:tab.getDisplayItems()) add(variants,stack);}
                catch(RuntimeException | LinkageError ex) {LOGGER.warn("Cannot read creative tab {}",tab.getDisplayName().getString(),ex);}
            }
        } catch(RuntimeException | LinkageError ex) {LOGGER.warn("Creative variants unavailable; retaining registered items",ex);}
        var represented=new HashSet<Item>();for(var stack:variants.values()) represented.add(stack.getItem());
        for(Item item:BuiltInRegistries.ITEM) if(item!=Items.AIR && !represented.contains(item)) add(variants,new ItemStack(item));
        return variants.values().stream().map(SceneCatalog::itemEntry)
            .sorted(Comparator.comparing(Entry::detail).thenComparing(e->e.label.getString())).toList();
    }
    private static void add(Map<String,ItemStack> entries,ItemStack stack) {
        if(stack.isEmpty()) return;ItemStack copy=stack.copy();copy.setCount(1);
        entries.putIfAbsent(copy.save(new CompoundTag()).toString(),copy);
    }
    public static Entry itemEntry(ItemStack stack) {
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();Component name=ItemLabels.name(stack);
        String variant="";
        if(stack.hasTag()) for(String key:List.of("GunId","ThrowableId","MeleeWeaponId")) if(stack.getTag().contains(key)) {variant=" · "+stack.getTag().getString(key);break;}
        return entry(PlacementSpec.item(stack),name,id+variant,stack.save(new CompoundTag()).toString());
    }
    public static List<Entry> hands() {
        var actor=StudioState.INSTANCE.actor();var entity=actor==null?null:StudioState.INSTANCE.entity(actor.id);
        if(!(entity instanceof LivingEntity living)) return List.of();var result=new ArrayList<Entry>();
        for(boolean off:new boolean[]{false,true}) {
            ItemStack stack=off?living.getOffhandItem():living.getMainHandItem();if(stack.isEmpty()) continue;
            var item=itemEntry(stack);Component label=Component.translatable(off?"posestudio.catalog.off_hand":"posestudio.catalog.main_hand").append(": ").append(item.label);
            result.add(entry(PlacementSpec.hand(entity.getUUID(),off),label,item.detail,item.search));
        }
        return List.copyOf(result);
    }
    private static Entry entry(PlacementSpec source,Component label,String detail,String extra) {
        return new Entry(source,label,detail,(label.getString()+" "+detail+" "+extra).toLowerCase(Locale.ROOT));
    }
    private SceneCatalog() {}
}
