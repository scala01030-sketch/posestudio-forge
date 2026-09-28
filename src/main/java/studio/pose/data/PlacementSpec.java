package studio.pose.data;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;


public record PlacementSpec(Kind kind, ResourceLocation type, ItemStack stack, UUID source) {
    public enum Kind { ENTITY, ITEM, MAIN_HAND, OFF_HAND }
    public static final UUID NONE=new UUID(0,0);
    public PlacementSpec { stack=stack.copy(); }
    @Override public ItemStack stack() { return stack.copy(); }
    public static PlacementSpec entity(ResourceLocation type) {return new PlacementSpec(Kind.ENTITY,type,ItemStack.EMPTY,NONE);}
    public static PlacementSpec item(ItemStack stack) {return new PlacementSpec(Kind.ITEM,new ResourceLocation("minecraft:item"),stack,NONE);}
    public static PlacementSpec hand(UUID source,boolean offHand) {return new PlacementSpec(offHand?Kind.OFF_HAND:Kind.MAIN_HAND,new ResourceLocation("minecraft:item"),ItemStack.EMPTY,source);}
}
