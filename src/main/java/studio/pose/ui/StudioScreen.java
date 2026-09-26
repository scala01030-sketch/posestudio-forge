package studio.pose.ui;

import java.util.*;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;
import studio.pose.client.*;
import studio.pose.data.*;
import studio.pose.overlay.StudioOverlay;
import studio.pose.serialization.PoseSerializer;

public final class StudioScreen extends Screen {
    private final StudioState s=StudioState.INSTANCE;
    private final List<EditBox> values=new ArrayList<>();
    private List<Entity> entities=List.of();
    private List<String> boneNames=List.of();
    private int left,right,actorScroll,boneScroll,boneStart;
    private String[] labels={};
    private EditBox poseName;
    private int propertyBottom;
    private int cameraDrag=-1;
    private Runnable cameraUndo;
    private boolean cameraChanged;
    public StudioScreen() { super(Component.translatable("posestudio.ui.title")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() { refresh(); }
    private Button button(String text,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(Component.translatable(text),b->action.run()).bounds(x,y,w,20).build());
    }
    public void refresh() {
        if(minecraft==null) return;
        clearWidgets();values.clear();left=Math.max(100,Math.min(152,width/4));right=Math.max(152,Math.min(190,width/3));
        button("posestudio.ui.actor",4,3,48,()->setMode(StudioState.Mode.ACTOR));
        button("posestudio.ui.pose",54,3,44,()->setMode(StudioState.Mode.POSE));
        button("posestudio.ui.camera",100,3,55,()->setMode(StudioState.Mode.CAMERA));
        button("posestudio.ui.scene",157,3,46,()->minecraft.setScreen(new EntityCatalogScreen()));
        button("posestudio.ui.undo",205,3,50,()->{s.undo.undo();refresh();});
        button(s.guides?"posestudio.ui.guides_on":"posestudio.ui.guides_off",257,3,72,()->{s.guides=!s.guides;refresh();});
        button("posestudio.ui.remove",331,3,60,s::removePlaced);
        button("posestudio.ui.capture",width-116,3,62,s::capture);
        button("posestudio.ui.exit",width-52,3,48,()->s.exit(true));
        String[] views={"front","back","left","right"};int viewWidth=(width-left-right-12)/4;
        for(int i=0;i<4;i++) {final int side=i;button("posestudio.ui.view_"+views[i],left+6+i*viewWidth,31,viewWidth-2,()->{studio.pose.camera.StudioViews.focus(side);if(s.mode==StudioState.Mode.CAMERA) refresh();});}
        Button freezeButton=button(s.selected!=null && s.placed.contains(s.selected)?"posestudio.ui.placed_frozen":s.actor()!=null && s.actor().frozen?"posestudio.ui.restore":"posestudio.ui.freeze",5,31,left-10,s::freeze);
        freezeButton.active=s.selected==null || !s.placed.contains(s.selected);
        rescan();boneStart=Math.max(110,height/2-6);
        ActorState actor=s.actor();
        labels=switch(s.mode) {
            case ACTOR -> new String[]{"posestudio.property.position_x","posestudio.property.position_y","posestudio.property.position_z","posestudio.property.yaw","posestudio.property.pitch","posestudio.property.roll"};
            case POSE -> new String[]{"posestudio.property.rotation_x","posestudio.property.rotation_y","posestudio.property.rotation_z","posestudio.property.local_x","posestudio.property.local_y","posestudio.property.local_z"};
            case CAMERA -> new String[]{"posestudio.property.position_x","posestudio.property.position_y","posestudio.property.position_z","posestudio.property.pitch","posestudio.property.yaw","posestudio.property.roll","posestudio.property.fov","posestudio.property.speed"};
        };
        double[] initial=currentValues();
        if(s.mode==StudioState.Mode.POSE && s.bone.matches("(left|right)_(elbow|knee)")) labels=Arrays.copyOf(labels,3);
        for(int i=0;i<labels.length;i++) {
            EditBox box=new EditBox(font,width-right+78,55+i*21,right-85,18,Component.translatable(labels[i]));
            box.setMaxLength(24);box.setValue(String.format(Locale.ROOT,"%.3f",initial[i]));values.add(addRenderableWidget(box));
            if(s.mode==StudioState.Mode.POSE && s.bone.matches("(left|right)_(elbow|knee)") && i>=3) box.setEditable(false);
        }
        propertyBottom=57+labels.length*21;
        button("posestudio.ui.apply",width-right+6,propertyBottom,right-12,this::apply);
        if(s.mode==StudioState.Mode.ACTOR) button(s.actorRotate?"posestudio.ui.actor_rotate":"posestudio.ui.actor_move",width-right+6,propertyBottom+24,right-12,()->{StudioOverlay.release();s.actorRotate=!s.actorRotate;refresh();});
        if(s.mode==StudioState.Mode.ACTOR) for(int axis=0;axis<3;axis++) {
            final int selectedAxis=axis;
            addRenderableWidget(Button.builder(Component.literal((axis==(s.actorRotate?s.actorRotationAxis:s.translationAxis)?"[":"")+"XYZ".charAt(axis)+(axis==(s.actorRotate?s.actorRotationAxis:s.translationAxis)?"]":"")),v->{StudioOverlay.release();if(s.actorRotate) s.actorRotationAxis=selectedAxis;else s.translationAxis=selectedAxis;refresh();})
                .bounds(width-right+6+axis*((right-12)/3),propertyBottom+48,(right-12)/3-2,20).tooltip(Tooltip.create(Component.translatable(s.actorRotate?"posestudio.ui.rotate_axis":"posestudio.ui.move_axis","XYZ".charAt(axis)))).build());
        }
        if(s.mode==StudioState.Mode.POSE) for(int axis=0;axis<3;axis++) {
            final int selectedAxis=axis;
            addRenderableWidget(Button.builder(Component.literal((axis==s.rotationAxis?"[":"")+"XYZ".charAt(axis)+(axis==s.rotationAxis?"]":"")),v->{s.rotationAxis=selectedAxis;refresh();})
                .bounds(width-right+6+axis*((right-12)/3),propertyBottom+24,(right-12)/3-2,20).build());
        }
        if(s.mode==StudioState.Mode.POSE && height-propertyBottom>120) {
            poseName=new EditBox(font,width-right+6,propertyBottom+50,right-12,18,Component.translatable("posestudio.ui.pose_filename"));poseName.setValue("pose");poseName.setMaxLength(64);addRenderableWidget(poseName);
            button("posestudio.ui.save",width-right+6,propertyBottom+72,(right-14)/2,()->file(false));
            button("posestudio.ui.load",width-right+8+(right-14)/2,propertyBottom+72,(right-14)/2,()->file(true));
        } else poseName=null;
        if(s.mode==StudioState.Mode.CAMERA) {
            button("posestudio.ui.fly",width-right+6,propertyBottom+24,right-12,s::fly);
            button("posestudio.ui.return_edit",width-right+6,propertyBottom+48,right-12,s::openEditor);
        }
    }
    public void rescan() {
        if(minecraft.level==null) return;
        List<Entity> list=new ArrayList<>();
        for(Entity e:minecraft.level.entitiesForRendering()) if(!e.isRemoved()) list.add(e);
        list.sort(Comparator.comparingDouble((Entity e)->e.position().distanceToSqr(new net.minecraft.world.phys.Vec3(s.camera.x,s.camera.y,s.camera.z))).thenComparingInt(Entity::getId));entities=list;
        actorScroll=Math.min(actorScroll,Math.max(0,entities.size()-1));
        ActorState a=s.actor();boneNames=a==null?List.of():new ArrayList<>(a.bones.keySet());
        if(a!=null && !s.guides) boneNames.removeIf(n->a.bones.containsKey("left_elbow")?(n.contains("/") || n.startsWith("emf$") || n.startsWith("f_")):!a.editableBones.contains(n));
        boneScroll=Math.min(boneScroll,Math.max(0,boneNames.size()-1));
    }
    public java.util.List<Entity> listedEntities() { return java.util.List.copyOf(entities); }
    private void setMode(StudioState.Mode mode) { cameraDrag=-1;s.setMode(mode);refresh(); }
    private double[] currentValues() {
        ActorState a=s.actor();
        if(s.mode==StudioState.Mode.CAMERA) { var c=s.camera;return new double[]{c.x,c.y,c.z,c.pitch,c.yaw,c.roll,c.fov,c.speed}; }
        if(s.mode==StudioState.Mode.ACTOR) { ActorTransform t=a==null?new ActorTransform(0,0,0,0,0):a.transform;return new double[]{t.x(),t.y(),t.z(),t.yaw(),t.pitch(),t.roll()}; }
        BonePose p=a==null?null:a.bones.get(s.bone);if(p==null) p=new BonePose();return new double[]{p.rotation[0],p.rotation[1],p.rotation[2],p.position[0],p.position[1],p.position[2]};
    }
    private void apply() {
        try {
            double[] v=new double[values.size()];for(int i=0;i<v.length;i++) { v[i]=Double.parseDouble(values.get(i).getValue());if(!Double.isFinite(v[i])) throw new IllegalArgumentException(); }
            if(s.mode==StudioState.Mode.ACTOR) {
                ActorTransform t=new ActorTransform(v[0],v[1],v[2],(float)v[3],(float)v[4],(float)v[5]);
                if(!t.valid() || s.actor()==null || !s.actor().frozen) throw new IllegalArgumentException();
                if(!s.movableSelection()) {s.message=Component.translatable("posestudio.status.freeze_selection");return;}
                var origin=s.selectedTransforms();var current=s.actor().transform;
                var targets=new ArrayList<studio.pose.network.StudioNetwork.Entry>();
                for(var entry:origin) {var previous=entry.transform();var next=new ActorTransform(previous.x()+t.x()-current.x(),previous.y()+t.y()-current.y(),previous.z()+t.z()-current.z(),previous.yaw()+t.yaw()-current.yaw(),previous.pitch()+t.pitch()-current.pitch(),previous.roll()+t.roll()-current.roll());if(!next.valid()) throw new IllegalArgumentException();targets.add(new studio.pose.network.StudioNetwork.Entry(entry.actor(),next));}
                s.undo.remember(s.undo.actorsSnapshot(origin));studio.pose.network.StudioNetwork.batch(false,targets);
            }
            if(s.mode==StudioState.Mode.POSE) {
                ActorState a=s.actor();if(a==null || !a.frozen) { s.message=Component.translatable("posestudio.status.freeze_first");return; }
                BonePose old=a.bones.get(s.bone);if(old==null) {s.message=Component.translatable("posestudio.status.select_bone");return;}
                BonePose p=new BonePose(v.length>3?v[3]:old.position[0],v.length>3?v[4]:old.position[1],v.length>3?v[5]:old.position[2],v[0],v[1],v[2]);if(!p.valid()) throw new IllegalArgumentException();
                if(!a.bones.containsKey(s.bone)) { s.message=Component.translatable("posestudio.status.select_bone");return; }
                s.undo.remember(s.undo.poseSnapshot(a));a.bones.put(s.bone,p);s.message=Component.translatable("posestudio.status.pose_applied");
            }
            if(s.mode==StudioState.Mode.CAMERA) {
                if(Math.abs(v[0])>29999984 || Math.abs(v[2])>29999984 || Math.abs(v[1])>2048 || Math.abs(v[3])>90 || Math.abs(v[4])>36000 || Math.abs(v[5])>36000 || v[6]<10 || v[6]>150 || v[7]<.1 || v[7]>100) throw new IllegalArgumentException();
                s.undo.remember(s.undo.cameraSnapshot());var c=s.camera;c.x=v[0];c.y=v[1];c.z=v[2];c.pitch=(float)v[3];c.yaw=(float)v[4];c.roll=(float)v[5];c.fov=v[6];c.speed=v[7];s.message=Component.translatable("posestudio.status.camera_updated");
            }
        } catch(IllegalArgumentException e) { s.message=Component.translatable("posestudio.status.number_range"); }
    }
    private void file(boolean load) {
        ActorState a=s.actor();Entity e=a==null?null:s.entity(a.id);
        if(a==null || !a.frozen || e==null || poseName==null) { s.message=Component.translatable("posestudio.status.select_frozen");return; }
        Path dir=minecraft.gameDirectory.toPath().resolve("posestudio/poses");String type=BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        try {
            if(load) {
                var doc=PoseSerializer.load(dir,poseName.getValue());
                if(!type.equals(doc.entity)) { s.message=Component.translatable("posestudio.status.type_mismatch");return; }
                s.undo.remember(s.undo.poseSnapshot(a));int applied=0;for(var entry:doc.bones.entrySet()) if(a.bones.containsKey(entry.getKey())) { a.bones.put(entry.getKey(),entry.getValue().copy());applied++; }
                // Loading a pose intentionally keeps the current staging position. Actor transform is saved for reference.
                s.message=Component.translatable("posestudio.status.loaded",applied); refresh();
            } else { PoseSerializer.save(dir,poseName.getValue(),type,a);s.message=Component.translatable("posestudio.status.saved",poseName.getValue()); }
        } catch(Exception ex) { s.message=StudioText.fileError(ex); }
    }
    @Override public void tick() { if(minecraft.level==null || !s.active) onClose(); else {boolean empty=boneNames.isEmpty();rescan();if(empty && !boneNames.isEmpty()) refresh();} }
    private String trim(String text,int pixels) { return font.plainSubstrByWidth(text,Math.max(1,pixels)); }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        StudioOverlay.draw(g,width,height,left,right);
        g.fill(0,0,width,27,0xe51a202b);g.fill(0,28,left,height-23,0xd91a202b);g.fill(width-right,28,width,height-23,0xd91a202b);g.fill(0,height-23,width,height,0xe51a202b);
        g.drawString(font,Component.translatable("posestudio.ui.actors_count",s.actors.values().stream().filter(a->a.frozen && !studio.pose.StudioConfig.prop(s.entity(a.id))).count(),studio.pose.StudioConfig.maxActors(),s.actors.values().stream().filter(a->a.frozen && studio.pose.StudioConfig.prop(s.entity(a.id))).count(),studio.pose.StudioConfig.maxProps()),6,57,0xffbdd5ef,false);
        int rows=Math.max(1,(boneStart-78)/15);
        for(int i=0;i<rows && i+actorScroll<entities.size();i++) {
            Entity e=entities.get(i+actorScroll);int y=72+i*15;boolean selected=s.selection.contains(e.getUUID());
            if(selected) g.fill(3,y-2,left-3,y+12,0xff3b536a);
            g.drawString(font,trim(e.getName().getString()+" #"+e.getId(),left-12),6,y,selected?0xffffd878:0xffeeeeee,false);
        }
        g.drawString(font,Component.translatable("posestudio.ui.bones"),6,boneStart,0xffbdd5ef,false);
        int boneRows=Math.max(0,(height-49-boneStart)/14);
        for(int i=0;i<boneRows && i+boneScroll<boneNames.size();i++) {
            String name=boneNames.get(i+boneScroll);int y=boneStart+16+i*14;
            if(name.equals(s.bone)) g.fill(3,y-2,left-3,y+11,0xff3b536a);
            g.drawString(font,trim(!s.guides && s.actor()!=null && !s.actor().bones.containsKey("left_elbow")?StudioText.leafBone(name):StudioText.bone(name),left-12),6,y,name.equals(s.bone)?0xffffd878:0xffeeeeee,false);
        }
        String title=s.mode==StudioState.Mode.POSE?StudioText.bone(s.bone):Component.translatable("posestudio.ui."+s.mode.name().toLowerCase(Locale.ROOT)).getString();
        g.drawString(font,trim(title,right-12),width-right+6,34,0xffffd878,false);
        for(int i=0;i<labels.length;i++) g.drawString(font,Component.translatable(labels[i]),width-right+6,60+i*21,0xffeeeeee,false);
        ActorState actor=s.actor();
        if(actor!=null && s.mode==StudioState.Mode.POSE && poseName==null) g.drawString(font,"Ctrl+S / Ctrl+O",width-right+6,propertyBottom+54,0xffbdd5ef,false);
        boolean missingRender=actor!=null && actor.frozen && actor.adapterReady && System.nanoTime()-Math.max(actor.renderRequestedNanos,actor.lastMainRenderNanos)>3_000_000_000L;
        Component footer=missingRender?Component.translatable("posestudio.adapter.not_visible"):s.message;
        g.drawString(font,trim(footer.getString(),width-12),6,height-20,0xffffdd99,false);
        g.drawString(font,trim(StudioKeys.shortcuts(s.mode==StudioState.Mode.ACTOR).getString(),width-12),6,height-10,0xffbdd5ef,false);
        if(actor!=null && (!actor.frozen || !actor.adapterReady || missingRender)) {
            Component status=missingRender?Component.translatable("posestudio.adapter.not_visible"):actor.adapterReady && !actor.frozen?Component.translatable("posestudio.adapter.not_frozen"):actor.adapterStatus;
            g.drawString(font,trim(status.getString(),width-left-right-12),left+6,57,0xffffdd99,false);
        }
        super.render(g,mx,my,partial);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(super.mouseClicked(x,y,button)) return true;
        if(viewport(x,y) && s.mode==StudioState.Mode.ACTOR && (button==0 || button==1) && StudioOverlay.actorClick(x,y,button,hasControlDown())) {refresh();return true;}
        if(viewport(x,y) && (button==1 || button==2)) { beginCameraDrag(button);return true; }
        if(button!=0) return false;
        if(x<left && y>=72 && y<72+Math.max(1,(boneStart-78)/15)*15) {
            int row=(int)(y-72)/15+actorScroll;if(row<entities.size()) { s.select(entities.get(row),s.mode==StudioState.Mode.ACTOR && hasControlDown());return true; }
        }
        if(x<left && y>=boneStart+16) {
            int row=(int)(y-boneStart-16)/14+boneScroll;if(row<boneNames.size()) { s.bone=boneNames.get(row);refresh();return true; }
        }
        if(x>left && x<width-right && y>28 && y<height-24 && StudioOverlay.click(x,y)) { refresh();return true; }
        if(viewport(x,y)) { beginCameraDrag(0);return true; }
        return false;
    }
    private boolean viewport(double x,double y) { return x>left && x<width-right && y>28 && y<height-24; }
    private void beginCameraDrag(int button) {cameraDrag=button;cameraChanged=false;cameraUndo=s.undo.cameraSnapshot();}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if((button==0 || button==1) && StudioOverlay.dragActor(x,y)) {updateNumbers();return true;}
        if(button==cameraDrag && cameraDrag>=0) {
            if(dx==0 && dy==0) return true;
            if(!cameraChanged) {s.undo.remember(cameraUndo);cameraChanged=true;}
            if(button==0) s.camera.look(dx,dy);
            else if(button==1) s.camera.pan(dx,dy);
            else s.camera.zoom(-dy*.25);
            if(s.mode==StudioState.Mode.CAMERA) updateNumbers();
            return true;
        }
        if(button==0 && StudioOverlay.drag(x,y)) { updateNumbers();return true; }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    private void updateNumbers() { double[] v=currentValues();for(int i=0;i<values.size();i++) values.get(i).setValue(String.format(Locale.ROOT,"%.3f",v[i])); }
    @Override public boolean mouseReleased(double x,double y,int button) { if(button==cameraDrag) cameraDrag=-1;StudioOverlay.release();return super.mouseReleased(x,y,button); }
    @Override public boolean mouseScrolled(double x,double y,double delta) {
        if(x<left) { if(y<boneStart) actorScroll=Math.max(0,Math.min(Math.max(0,entities.size()-1),actorScroll-(int)Math.signum(delta)));else boneScroll=Math.max(0,Math.min(Math.max(0,boneNames.size()-1),boneScroll-(int)Math.signum(delta)));return true; }
        if(viewport(x,y)) { if(delta!=0) {s.undo.remember(s.undo.cameraSnapshot());s.camera.zoom(delta);}if(s.mode==StudioState.Mode.CAMERA) updateNumbers();return true; }
        return super.mouseScrolled(x,y,delta);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(hasControlDown() && key==GLFW.GLFW_KEY_Z) {StudioOverlay.release();s.undo.undo();refresh();return true;}
        if(StudioKeys.press(key,scan)) return true;
        if(key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER) { apply();return true; }
        if(hasControlDown() && (key==GLFW.GLFW_KEY_S || key==GLFW.GLFW_KEY_O)) {
            // Short screens still get the complete file workflow through a dedicated filename dialog.
            minecraft.setScreen(new PoseFileScreen(key==GLFW.GLFW_KEY_O,this));return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void onClose() {StudioOverlay.release();cameraDrag=-1;s.fly();}
}
