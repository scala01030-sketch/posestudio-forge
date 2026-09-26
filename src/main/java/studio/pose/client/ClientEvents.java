package studio.pose.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import studio.pose.PoseStudio;
import studio.pose.overlay.StudioOverlay;

@Mod.EventBusSubscriber(modid=PoseStudio.ID,value=Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping TOGGLE=new KeyMapping("key.posestudio.toggle",GLFW.GLFW_KEY_F6,"key.categories.posestudio");
    public static final KeyMapping EDITOR=new KeyMapping("key.posestudio.editor",GLFW.GLFW_KEY_F7,"key.categories.posestudio");
    public static final KeyMapping CAPTURE=new KeyMapping("key.posestudio.capture",GLFW.GLFW_KEY_F8,"key.categories.posestudio");
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        StudioState s=StudioState.INSTANCE; Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null) { if(s.active || s.entering()) s.exit(false); return; }
        while(TOGGLE.consumeClick()) s.toggle();
        while(EDITOR.consumeClick()) s.editor();
        while(CAPTURE.consumeClick()) s.capture();
        if(s.active || s.entering()) s.tick();
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e) {
        if(e.phase==TickEvent.Phase.START && StudioState.INSTANCE.active) StudioState.INSTANCE.camera.frame();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { StudioState.INSTANCE.exit(false); }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e) {
        if(!StudioState.INSTANCE.active) return;
        var c=StudioState.INSTANCE.camera; e.setYaw(c.yaw); e.setPitch(c.pitch); e.setRoll(c.roll);
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e) { if(StudioState.INSTANCE.active && e.usedConfiguredFov()) e.setFOV(StudioState.INSTANCE.camera.fov); }
    @SubscribeEvent public static void hand(RenderHandEvent e) { if(StudioState.INSTANCE.active) e.setCanceled(true); }
    @SubscribeEvent public static void chat(RenderGuiOverlayEvent.Pre e) {
        if(StudioState.INSTANCE.active && e.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CHAT_PANEL.id())) e.setCanceled(true);
    }
    @SubscribeEvent public static void stage(RenderLevelStageEvent e) { VisibleActors.stage(e);StudioOverlay.stage(e); }
    @Mod.EventBusSubscriber(modid=PoseStudio.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(TOGGLE); e.register(EDITOR); e.register(CAPTURE); }
    }
}
