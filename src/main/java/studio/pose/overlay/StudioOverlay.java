package studio.pose.overlay;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.*;
import java.lang.Math;
import studio.pose.client.*;
import studio.pose.data.BonePose;


public final class StudioOverlay {
    private static Matrix4f projection=new Matrix4f(),view=new Matrix4f();
    private static Vec3 camera=Vec3.ZERO;
    private static int width,height;
    private static long frame;
    private static final int[] COLORS={0xffff6060,0xff60ef80,0xff609aff};
    public record Point(double x,double y) {}
    private record RingSegment(Point a,Point b,int axis) {}
    private static final List<RingSegment> rings=new ArrayList<>();
    private static int dragging=-1;
    private static double lastAngle;
    private static Matrix4f dragMatrix;
    private static Point lastCursor;
    private static Runnable dragUndo;
    private static boolean dragChanged;
    private static java.util.List<studio.pose.network.StudioNetwork.Entry> actorOrigin=List.of();
    private static Vector3f actorCenter;
    private static double actorStart,actorMouseY,actorWorldPerPixel;
    private static int actorAxis=-1;
    private static boolean actorParallel;
    private static Matrix4f actorInverse;
    private static Runnable actorUndo;
    private static boolean actorChanged;
    private static boolean actorRotation;
    private static double actorRotationAmount;
    private static Matrix4f actorRotationMatrix;
    private static Point actorCursor;
    public static void stage(RenderLevelStageEvent e) {
        if(!StudioState.INSTANCE.active || studio.pose.model.ShaderPass.shadow() || e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        projection=new Matrix4f(e.getProjectionMatrix()); view=new Matrix4f(e.getPoseStack().last().pose()); camera=e.getCamera().getPosition(); frame=System.nanoTime();
    }
    private static Point project(Matrix4f matrix,Vector3f p) {
        Vector4f v=new Vector4f(p,1); new Matrix4f(projection).mul(matrix).transform(v);
        if(v.w<=.001 || v.z < -v.w || v.z>v.w) return null;
        return new Point((v.x/v.w*.5+.5)*width,(.5-v.y/v.w*.5)*height);
    }
    private static Point projectWorld(Vec3 v) { return project(view,new Vector3f((float)(v.x-camera.x),(float)(v.y-camera.y),(float)(v.z-camera.z))); }
    private static Point node(ActorState.Node n) { return project(n.handleMatrix(),new Vector3f()); }
    public static Point rotationHandle(int axis,double angle) {
        ActorState a=StudioState.INSTANCE.actor();if(a==null) return null;
        ActorState.Node n=a.nodes.get(StudioState.INSTANCE.bone);
        return n==null?null:project(n.handleMatrix(),ring(axis,angle));
    }
    public static void draw(GuiGraphics g,int w,int h,int left,int right) {
        width=w;height=h;rings.clear();
        StudioState s=StudioState.INSTANCE;if(!s.active || s.capture || System.nanoTime()-frame>1_000_000_000L) return;
        g.enableScissor(left,28,w-right,h-24);
        ActorState a=s.actor();
        Entity entity=a==null?null:s.entity(a.id);
        if(entity!=null && (s.mode==StudioState.Mode.ACTOR || s.guides)) for(UUID id:s.selection) {
            Entity member=s.entity(id);if(member==null) continue;
            var box=member.getBoundingBox(); Vec3[] corners=new Vec3[8];
            for(int i=0;i<8;i++) corners[i]=studio.pose.model.ActorOrientation.point(member,new Vec3((i&1)==0?box.minX:box.maxX,(i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ),false);
            for(int i=0;i<8;i++) for(int axis=0;axis<3;axis++) if((i&(1<<axis))==0) line(g,projectWorld(corners[i]),projectWorld(corners[i|(1<<axis)]),0xfff4cb62);
        }
        if(a!=null && a.frozen && s.mode==StudioState.Mode.POSE) {
            for(ActorState.Node n:a.nodes.values()) {
                if(!visibleNode(n)) continue;
                Point p=node(n); if(p==null) continue;
                ActorState.Node parent=a.nodes.get(n.parent()); if(s.guides && parent!=null) line(g,p,node(parent),0xff9db0c4);
                int x=(int)p.x,y=(int)p.y;boolean chosen=n.name().equals(s.bone);
                g.fill(x-3,y-3,x+4,y+4,chosen?0xffffcc55:0xffeef6ff);
            }
            ActorState.Node n=a.nodes.get(s.bone);if(n!=null) drawRings(g,n.handleMatrix());
        }
        if(entity!=null && a.frozen && s.mode==StudioState.Mode.ACTOR && s.actorRotate) drawRings(g,actorRingMatrix(),s.actorRotationAxis);
        if(entity!=null && a.frozen && s.mode==StudioState.Mode.ACTOR && !s.actorRotate) {
            Point origin=actorPoint(a.id);
            for(int axis=0;axis<3;axis++) {
                Point end=translationHandle(axis,1);line(g,origin,end,COLORS[axis]);if(end==null) continue;
                int x=(int)end.x,y=(int)end.y;
                if(axis==s.translationAxis) g.fill(x-5,y-5,x+6,y+6,0xffeeeeee);
                g.fill(x-3,y-3,x+4,y+4,COLORS[axis]);
                g.drawString(Minecraft.getInstance().font,String.valueOf("XYZ".charAt(axis)),x+7,y-4,COLORS[axis],true);
            }
        }
        g.disableScissor();
    }
    private static boolean visibleNode(ActorState.Node n) {
        var s=StudioState.INSTANCE;var actor=s.actor();
        return s.guides || actor==null || (actor.bones.containsKey("left_elbow")?(!n.name().contains("/") && !n.name().startsWith("emf$") && !n.name().startsWith("f_")):actor.editableBones.contains(n.name()));
    }
    public static Point jointPoint(String name) {var actor=StudioState.INSTANCE.actor();return actor==null || !actor.nodes.containsKey(name)?null:node(actor.nodes.get(name));}
    private static Vector3f ring(int axis,double angle) {
        float x=(float)Math.cos(angle)*.28f,y=(float)Math.sin(angle)*.28f;
        return switch(axis) { case 0 -> new Vector3f(0,x,y); case 1 -> new Vector3f(y,0,x); default -> new Vector3f(x,y,0); };
    }
    private static void drawRings(GuiGraphics g,Matrix4f m) {
        drawRings(g,m,StudioState.INSTANCE.rotationAxis);
    }
    private static void drawRings(GuiGraphics g,Matrix4f m,int axis) {
        for(int i=0;i<64;i++) {
            Point a=project(m,ring(axis,i*Math.PI/32)),b=project(m,ring(axis,(i+1)*Math.PI/32));
            if(a!=null && b!=null) { rings.add(new RingSegment(a,b,axis));line(g,a,b,COLORS[axis]); }
        }
    }
    private static void line(GuiGraphics g,Point a,Point b,int color) {
        if(a==null || b==null || Math.abs(a.x)>100000 || Math.abs(b.x)>100000 || Math.abs(a.y)>100000 || Math.abs(b.y)>100000) return;
        int steps=Math.min(2000,(int)Math.max(Math.abs(a.x-b.x),Math.abs(a.y-b.y))+1);
        for(int i=0;i<=steps;i++) { double t=(double)i/steps;int x=(int)(a.x+(b.x-a.x)*t),y=(int)(a.y+(b.y-a.y)*t);g.fill(x,y,x+1,y+1,color); }
    }
    private static double distance(Point p,Point a,Point b) {
        double dx=b.x-a.x,dy=b.y-a.y,den=dx*dx+dy*dy;
        double t=den<.0001?0:Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.y-a.y)*dy)/den));
        return Math.hypot(p.x-a.x-dx*t,p.y-a.y-dy*t);
    }
    public static boolean click(double x,double y) {
        StudioState s=StudioState.INSTANCE;ActorState a=s.actor();Point cursor=new Point(x,y);
        if(s.mode==StudioState.Mode.POSE && a!=null && a.frozen) {

            ActorState.Node closest=null;double nodeDistance=6;
            for(var n:a.nodes.values()) {if(!visibleNode(n)) continue;Point p=node(n);if(p!=null && Math.hypot(p.x-x,p.y-y)<nodeDistance) {nodeDistance=Math.hypot(p.x-x,p.y-y);closest=n;}}
            if(closest!=null) {s.bone=closest.name();return true;}
            RingSegment hit=null; double nearest=6;
            for(RingSegment segment:rings) { double d=distance(cursor,segment.a,segment.b);if(d<nearest) { nearest=d;hit=segment; } }
            ActorState.Node selected=a.nodes.get(s.bone);
            if(hit!=null && selected!=null) {
                dragMatrix=new Matrix4f(selected.handleMatrix());dragging=hit.axis;lastAngle=planeAngle(x,y,dragMatrix,dragging);
                lastCursor=cursor;dragUndo=s.undo.poseSnapshot(a);dragChanged=false;
                return true;
            }
        }
        Entity picked=pickEntity(x,y);if(picked!=null) { s.select(picked);return true; }return false;
    }

    public static boolean actorClick(double x,double y,int button,boolean additive) {
        var s=StudioState.INSTANCE;
        if(!additive && s.actorRotate && s.movableSelection()) {
            for(var segment:rings) if(distance(new Point(x,y),segment.a,segment.b)<7) return beginActorRotation(x,y);
        }
        if(!additive && !s.actorRotate && s.actor()!=null && s.movableSelection()) {
            Point origin=actorPoint(s.selected);int hit=-1;double nearest=7;
            for(int axis=0;axis<3;axis++) {Point end=translationHandle(axis,1),inner=translationHandle(axis,.25);if(origin==null || end==null || inner==null) continue;double d=distance(new Point(x,y),inner,end);if(d<nearest) {nearest=d;hit=axis;}}
            if(hit>=0) {s.translationAxis=hit;return beginActor(x,y);}
        }
        Entity picked=pickEntity(x,y);if(picked==null) return false;
        if(additive) {s.select(picked,true);return true;}
        if(!s.selection.contains(picked.getUUID())) s.select(picked);
        if(!s.movableSelection()) {s.message=net.minecraft.network.chat.Component.translatable("posestudio.status.freeze_selection");return true;}
        return s.actorRotate?beginActorRotation(x,y):beginActor(x,y);
    }
    private static Matrix4f actorRingMatrix() {
        var s=StudioState.INSTANCE;Entity e=s.entity(s.selected);if(e==null) return new Matrix4f(view);
        Vec3 center=studio.pose.model.ActorOrientation.point(e,e.getBoundingBox().getCenter(),false);double radius=Math.max(.35,center.distanceTo(camera)*Math.tan(Math.toRadians(s.camera.fov/2))*2*55/Math.max(1,height));
        return new Matrix4f(view).translate((float)(center.x-camera.x),(float)(center.y-camera.y),(float)(center.z-camera.z)).scale((float)(radius/.28));
    }
    public static Point actorRotationHandle(int axis,double angle) {return project(actorRingMatrix(),ring(axis,angle));}
    private static boolean beginActorRotation(double x,double y) {
        var s=StudioState.INSTANCE;actorOrigin=s.selectedTransforms();actorAxis=s.actorRotationAxis;actorRotation=true;actorRotationAmount=0;actorRotationMatrix=actorRingMatrix();
        actorStart=planeAngle(x,y,actorRotationMatrix,actorAxis);actorCursor=new Point(x,y);actorUndo=s.undo.actorsSnapshot(actorOrigin);actorChanged=false;return true;
    }
    private static boolean beginActor(double x,double y) {
        var s=StudioState.INSTANCE;var entity=s.entity(s.selected);if(entity==null) return false;
        actorRotation=false;actorOrigin=s.selectedTransforms();actorAxis=s.translationAxis;actorInverse=new Matrix4f(projection).mul(view).invert();
        Vec3 center=studio.pose.model.ActorOrientation.point(entity,entity.getBoundingBox().getCenter(),false);actorCenter=new Vector3f((float)(center.x-camera.x),(float)(center.y-camera.y),(float)(center.z-camera.z));
        actorMouseY=y;actorWorldPerPixel=Math.max(.001,center.distanceTo(camera)*Math.tan(Math.toRadians(s.camera.fov/2))*2/height);
        actorStart=axisParameter(x,y);actorParallel=!Double.isFinite(actorStart);actorUndo=s.undo.actorsSnapshot(actorOrigin);actorChanged=false;return true;
    }
    private static double axisParameter(double x,double y) {
        Vector3f near=unproject(x,y,-1,actorInverse),dir=unproject(x,y,1,actorInverse).sub(near).normalize(),w=new Vector3f(near).sub(actorCenter);
        double b=dir.get(actorAxis),den=1-b*b;
        return den<.02?Double.NaN:(w.get(actorAxis)-b*dir.dot(w))/den;
    }
    public static boolean actorDragging() {return actorAxis>=0;}
    public static boolean dragActor(double x,double y) {
        if(actorAxis<0) return false;
        if(actorRotation) {
            double angle=planeAngle(x,y,actorRotationMatrix,actorAxis);
            double delta=Double.isFinite(angle) && Double.isFinite(actorStart)?Math.toDegrees(Math.atan2(Math.sin(angle-actorStart),Math.cos(angle-actorStart))):(x-actorCursor.x)-(y-actorCursor.y);
            actorStart=angle;actorCursor=new Point(x,y);if(!Double.isFinite(delta) || Math.abs(delta)<.000001) return true;
            if(!actorChanged) {StudioState.INSTANCE.undo.remember(actorUndo);actorChanged=true;}
            actorRotationAmount+=delta;var moved=new ArrayList<studio.pose.network.StudioNetwork.Entry>();
            for(var entry:actorOrigin) {var t=entry.transform();var next=new studio.pose.data.ActorTransform(t.x(),t.y(),t.z(),t.yaw()+(actorAxis==1?(float)actorRotationAmount:0),t.pitch()+(actorAxis==0?(float)actorRotationAmount:0),t.roll()+(actorAxis==2?(float)actorRotationAmount:0));if(!next.valid()) return true;moved.add(new studio.pose.network.StudioNetwork.Entry(entry.actor(),next));}
            studio.pose.network.StudioNetwork.batch(false,moved);return true;
        }
        var s=StudioState.INSTANCE;double amount=actorParallel?(actorMouseY-y)*actorWorldPerPixel:axisParameter(x,y)-actorStart;
        if(!Double.isFinite(amount)) return true;
        Vector3f delta=new Vector3f();delta.setComponent(actorAxis,(float)amount);
        if(!actorChanged && delta.lengthSquared()<.000001f) return true;
        if(!actorChanged) {s.undo.remember(actorUndo);actorChanged=true;}
        s.moveSelection(actorOrigin,new Vec3(delta.x,delta.y,delta.z));return true;
    }
    public static Point actorPoint(UUID id) {Entity e=StudioState.INSTANCE.entity(id);return e==null?null:projectWorld(studio.pose.model.ActorOrientation.point(e,e.getBoundingBox().getCenter(),false));}

    public static Point translationHandle(int axis,double fraction) {
        var s=StudioState.INSTANCE;Entity e=s.entity(s.selected);if(e==null) return null;
        Vec3 center=studio.pose.model.ActorOrientation.point(e,e.getBoundingBox().getCenter(),false);Point origin=projectWorld(center);if(origin==null) return null;
        double length=Math.max(.4,center.distanceTo(camera)*Math.tan(Math.toRadians(s.camera.fov/2))*2*55/Math.max(1,height));
        Vec3 delta=switch(axis) {case 0->new Vec3(length,0,0);case 1->new Vec3(0,length,0);default->new Vec3(0,0,length);};
        Point end=projectWorld(center.add(delta));
        if(end==null || Math.hypot(end.x-origin.x,end.y-origin.y)<12) return new Point(origin.x+25*fraction,origin.y+25*fraction);
        return new Point(origin.x+(end.x-origin.x)*fraction,origin.y+(end.y-origin.y)*fraction);
    }
    public static boolean drag(double x,double y) {
        if(dragging<0) return false;
        ActorState a=StudioState.INSTANCE.actor(); if(a==null) return false;
        BonePose pose=a.bones.get(StudioState.INSTANCE.bone); if(pose==null) return false;
        double angle=planeAngle(x,y,dragMatrix,dragging);
        double delta=Double.isFinite(angle) && Double.isFinite(lastAngle)?Math.toDegrees(Math.atan2(Math.sin(angle-lastAngle),Math.cos(angle-lastAngle))):((x-lastCursor.x)-(y-lastCursor.y));
        if(Double.isFinite(delta) && Math.abs(delta)>.000001) {
            if(!dragChanged) {StudioState.INSTANCE.undo.remember(dragUndo);dragChanged=true;}
            pose.rotation[dragging]+=delta;
        }
        lastAngle=angle;lastCursor=new Point(x,y);return true;
    }
    public static void release() { dragging=-1;dragUndo=null;lastCursor=null;actorAxis=-1;actorUndo=null;actorOrigin=List.of(); }
    private static double planeAngle(double x,double y,Matrix4f matrix,int axis) {
        Matrix4f inv=new Matrix4f(projection).mul(matrix).invert();
        Vector3f a=unproject(x,y,-1,inv),b=unproject(x,y,1,inv),dir=new Vector3f(b).sub(a);
        float denominator=dir.get(axis); if(Math.abs(denominator)<.00001) return Double.NaN;
        float t=-a.get(axis)/denominator;Vector3f p=new Vector3f(dir).mul(t).add(a);
        return switch(axis) { case 0 -> Math.atan2(p.z,p.y);case 1 -> Math.atan2(p.x,p.z);default -> Math.atan2(p.y,p.x); };
    }
    private static Vector3f unproject(double x,double y,float z,Matrix4f inverse) {
        Vector4f p=new Vector4f((float)(2*x/width-1),(float)(1-2*y/height),z,1);inverse.transform(p);return new Vector3f(p.x/p.w,p.y/p.w,p.z/p.w);
    }
    private static Entity pickEntity(double x,double y) {
        Minecraft mc=Minecraft.getInstance();if(mc.level==null) return null;
        Matrix4f inverse=new Matrix4f(projection).mul(view).invert();
        Vector3f far=unproject(x,y,1,inverse);
        Vec3 start=camera,end=camera.add(far.x,far.y,far.z);
        Entity found=null;double best=Double.MAX_VALUE;
        for(Entity e:mc.level.entitiesForRendering()) {
            var hit=e.getBoundingBox().inflate(.1).clip(studio.pose.model.ActorOrientation.point(e,start,true),studio.pose.model.ActorOrientation.point(e,end,true));
            if(hit.isPresent()) { double distance=studio.pose.model.ActorOrientation.point(e,hit.get(),false).distanceToSqr(start);if(distance<best) { best=distance;found=e; } }
        } return found;
    }
}
