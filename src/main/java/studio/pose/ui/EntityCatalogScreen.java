package studio.pose.ui;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import studio.pose.client.SceneCatalog;
import studio.pose.client.StudioState;
import studio.pose.data.ActorTransform;
import studio.pose.network.StudioNetwork;


public final class EntityCatalogScreen extends Screen {
    private List<SceneCatalog.Entry> entries=List.of(),allEntries=List.of(),itemEntries;
    private SceneCatalog.Entry selected;
    public enum Category { ENTITY, ITEM }
    private Category category=Category.ENTITY;
    private EditBox search;
    private final List<EditBox> coordinates=new ArrayList<>();
    private int scroll,listWidth;
    public EntityCatalogScreen() { super(Component.translatable("posestudio.catalog.title")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        listWidth=Math.min(300,width/2-8);coordinates.clear();
        search=addRenderableWidget(new EditBox(font,8,31,listWidth-16,20,Component.translatable("posestudio.catalog.search")));
        search.setHint(Component.translatable("posestudio.catalog.search"));search.setMaxLength(100);search.setResponder(value->{scroll=0;filter(value);});category(Category.ENTITY);
        int tabWidth=Math.min(120,(width-listWidth-24)/2);
        String[] tabs={"entities","items"};
        for(int i=0;i<2;i++) {final Category target=Category.values()[i];
            addRenderableWidget(Button.builder(Component.translatable("posestudio.catalog."+tabs[i]),b->category(target)).bounds(listWidth+16+i*tabWidth,31,tabWidth-2,20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("posestudio.catalog.camera"),b->fromCamera()).bounds(listWidth+16,56,180,20).build());
        var t=StudioState.INSTANCE.placementInView();double[] v={t.x(),t.y(),t.z(),t.yaw(),t.pitch()};
        for(int i=0;i<v.length;i++) {
            EditBox box=addRenderableWidget(new EditBox(font,listWidth+100,85+i*25,96,20,Component.literal("XYZ")));
            box.setMaxLength(24);box.setValue(String.format(Locale.ROOT,"%.3f",v[i]));coordinates.add(box);
        }
        addRenderableWidget(Button.builder(Component.translatable("posestudio.catalog.place"),b->place()).bounds(listWidth+16,221,180,20).build());
        addRenderableWidget(Button.builder(Component.translatable("posestudio.catalog.back"),b->onClose()).bounds(width-90,6,82,20).build());
    }
    private void filter(String value) {
        entries=allEntries.stream().filter(e->e.matches(value)).toList();selected=null;
    }
    public void category(Category value) {
        category=value;scroll=0;selected=null;
        allEntries=switch(value) {
            case ENTITY -> SceneCatalog.entities();
            case ITEM -> {if(itemEntries==null) itemEntries=SceneCatalog.items();yield itemEntries;}
        };
        search.setValue("");filter("");
    }
    public List<SceneCatalog.Entry> listedEntries() {return entries;}
    private void fromCamera() {
        var t=StudioState.INSTANCE.placementInView();double[] v={t.x(),t.y(),t.z(),t.yaw(),t.pitch()};
        for(int i=0;i<v.length;i++) coordinates.get(i).setValue(String.format(Locale.ROOT,"%.3f",v[i]));
    }
    private void place() {
        var s=StudioState.INSTANCE;
        try {
            if(selected==null) {s.message=Component.translatable("posestudio.catalog.select");return;}
            double[] v=new double[5];for(int i=0;i<5;i++) v[i]=Double.parseDouble(coordinates.get(i).getValue());
            var t=new ActorTransform(v[0],v[1],v[2],(float)v[3],(float)v[4]);if(!t.valid()) throw new IllegalArgumentException();
            StudioNetwork.place(selected.source(),t);s.message=Component.translatable("posestudio.status.placing");onClose();
        } catch(IllegalArgumentException ex) {s.message=Component.translatable("posestudio.status.number_range");}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        g.fill(0,0,width,height,0xc51a202b);g.drawString(font,title.copy().append(" ("+entries.size()+")"),8,10,0xffffd878,false);
        int rows=Math.max(1,(height-95)/28);
        for(int i=0;i<rows && i+scroll<entries.size();i++) {
            var entry=entries.get(i+scroll);int y=61+i*28;
            if(entry==selected) g.fill(5,y-2,listWidth-5,y+25,0xff3b536a);
            g.drawString(font,font.plainSubstrByWidth(entry.label().getString(),listWidth-16),8,y,0xffeeeeee,false);
            g.drawString(font,font.plainSubstrByWidth(entry.detail(),listWidth-16),8,y+12,0xffbdd5ef,false);
        }
        String[] keys={"position_x","position_y","position_z","yaw","pitch"};
        for(int i=0;i<keys.length;i++) g.drawString(font,Component.translatable("posestudio.property."+keys[i]),listWidth+16,91+i*25,0xffeeeeee,false);
        if(selected!=null) g.drawString(font,font.plainSubstrByWidth(selected.label().getString(),width-listWidth-24),listWidth+16,250,0xffffd878,false);
        g.drawWordWrap(font,Component.translatable("posestudio.catalog.no_hand"),listWidth+16,270,width-listWidth-24,0xffbdd5ef);
        g.drawString(font,font.plainSubstrByWidth(Component.translatable("posestudio.catalog.hint").getString(),width-16),8,height-30,0xffbdd5ef,false);
        g.drawString(font,font.plainSubstrByWidth(StudioState.INSTANCE.message.getString(),width-16),8,height-16,0xffffdd99,false);
        super.render(g,mx,my,partial);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(super.mouseClicked(x,y,button)) return true;
        if(button==0 && x>=5 && x<listWidth && y>=59 && y<61+Math.max(1,(height-95)/28)*28) {
            int index=(int)(y-61)/28+scroll;if(index>=0 && index<entries.size()) selected=entries.get(index);return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x,double y,double delta) {
        if(x<listWidth) {scroll=Math.max(0,Math.min(Math.max(0,entries.size()-Math.max(1,(height-95)/28)),scroll-(int)Math.signum(delta)));return true;}
        return super.mouseScrolled(x,y,delta);
    }
    @Override public void onClose() {minecraft.setScreen(new StudioScreen());}
}
