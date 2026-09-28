package studio.pose.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import studio.pose.client.StudioKeys;
import studio.pose.client.StudioState;


public final class StudioHud {
    private static long lastFrame;
    private StudioHud() {}
    public static long lastFrameNanos() {return lastFrame;}
    public static void draw() {
        var mc=Minecraft.getInstance();var s=StudioState.INSTANCE;
        if(!s.active || s.capture || mc.screen!=null || mc.level==null) return;
        var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());int width=mc.getWindow().getGuiScaledWidth();
        g.fill(0,0,width,17,0xb01a202b);
        g.drawString(mc.font,mc.font.plainSubstrByWidth(StudioKeys.returnHint().getString(),width-12),6,5,0xffbdd5ef,false);
        g.flush();lastFrame=System.nanoTime();
    }
}
