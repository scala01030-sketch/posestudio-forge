package studio.pose.ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import studio.pose.client.StudioState;
import studio.pose.serialization.PoseSerializer;
public final class PoseFileScreen extends Screen {
    private final boolean load;private final Screen parent;private EditBox name;
    public PoseFileScreen(boolean load,Screen parent) { super(Component.translatable(load?"posestudio.ui.load_pose":"posestudio.ui.save_pose"));this.load=load;this.parent=parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        name=new EditBox(font,width/2-90,height/2-18,180,20,Component.translatable("posestudio.ui.filename"));name.setValue("pose");addRenderableWidget(name);
        addRenderableWidget(Button.builder(title,b->apply()).bounds(width/2-90,height/2+8,88,20).build());
        addRenderableWidget(Button.builder(Component.translatable("posestudio.ui.cancel"),b->onClose()).bounds(width/2+2,height/2+8,88,20).build());
    }
    private void apply() {
        var s=StudioState.INSTANCE;var a=s.actor();var e=a==null?null:s.entity(a.id);
        if(a==null || !a.frozen || e==null) { s.message=Component.translatable("posestudio.status.select_frozen");onClose();return; }
        try {
            var dir=minecraft.gameDirectory.toPath().resolve("posestudio/poses");String type=BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
            if(load) {
                var d=PoseSerializer.load(dir,name.getValue());if(!type.equals(d.entity)) throw new java.io.IOException("posestudio.status.type_mismatch");
                s.undo.remember(s.undo.poseSnapshot(a));d.bones.forEach((k,v)->{if(a.bones.containsKey(k)) a.bones.put(k,v.copy());});s.message=Component.translatable("posestudio.status.pose_loaded");
            } else { PoseSerializer.save(dir,name.getValue(),type,a);s.message=Component.translatable("posestudio.status.saved",name.getValue()); }
        } catch(Exception ex) { s.message=StudioText.fileError(ex); }onClose();
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial) { g.fill(width/2-110,height/2-48,width/2+110,height/2+44,0xf019202b);g.drawCenteredString(font,title,width/2,height/2-36,0xffffffff);super.render(g,x,y,partial); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
