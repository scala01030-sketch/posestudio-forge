package studio.pose.client;

import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;


public final class StudioKeys {
    private StudioKeys() {}
    public static boolean mapped(int key,int scan) {return ClientEvents.TOGGLE.matches(key,scan) || ClientEvents.EDITOR.matches(key,scan) || ClientEvents.CAPTURE.matches(key,scan);}
    public static boolean press(int key,int scan) {
        var s=StudioState.INSTANCE;
        if(ClientEvents.TOGGLE.matches(key,scan)) {s.toggle();return true;}
        if(!s.active) return false;
        if(ClientEvents.EDITOR.matches(key,scan)) {s.editor();return true;}
        if(ClientEvents.CAPTURE.matches(key,scan)) {s.capture();return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE && net.minecraft.client.Minecraft.getInstance().screen==null) {s.openEditor();return true;}
        return false;
    }
    public static Component shortcuts(boolean actor) {
        return Component.translatable(actor?"posestudio.ui.actor_shortcuts":"posestudio.ui.shortcuts",ClientEvents.EDITOR.getTranslatedKeyMessage(),ClientEvents.CAPTURE.getTranslatedKeyMessage(),ClientEvents.TOGGLE.getTranslatedKeyMessage());
    }
    public static boolean mousePress(int button) {
        var s=StudioState.INSTANCE;
        if(ClientEvents.TOGGLE.matchesMouse(button)) {s.toggle();return true;}
        if(!s.active) return false;
        if(ClientEvents.EDITOR.matchesMouse(button)) {s.editor();return true;}
        if(ClientEvents.CAPTURE.matchesMouse(button)) {s.capture();return true;}
        return false;
    }
    public static Component returnHint() {return Component.translatable("posestudio.ui.return_hint",ClientEvents.EDITOR.getTranslatedKeyMessage(),ClientEvents.CAPTURE.getTranslatedKeyMessage(),ClientEvents.TOGGLE.getTranslatedKeyMessage());}
}
