package studio.pose.model;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import studio.pose.client.ActorState;
import studio.pose.data.BonePose;
import studio.pose.mesh.BendMath;
import org.joml.*;
import java.lang.Math;

public final class PoseController {
    public record Saved(ModelPart part,float x,float y,float z,float rx,float ry,float rz,float sx,float sy,float sz) {
        void restore() { part.x=x;part.y=y;part.z=z;part.xRot=rx;part.yRot=ry;part.zRot=rz;part.xScale=sx;part.yScale=sy;part.zScale=sz; }
    }
    private static final Deque<Saved> SAVED=new ArrayDeque<>();
    private static final float DEG=(float)(Math.PI/180);
    public static void before(ModelPart p) {
        var binding=RenderContext.binding(p); if(binding==null) return;
        // An optimizer may cancel an unbound part before our RETURN hook. Never retain state for it.
        SAVED.push(new Saved(p,p.x,p.y,p.z,p.xRot,p.yRot,p.zRot,p.xScale,p.yScale,p.zScale));
        ActorState a=RenderContext.current().actor();
        BonePose pose=a.bones.computeIfAbsent(binding.name(),n->{
            // A newly frozen humanoid starts in its model's rest pose, not a cached weapon animation.
            if(RenderContext.current().adapter().humanoid) {
                var rest=p.getInitialPose();return new BonePose(rest.x,rest.y,rest.z,rest.xRot/DEG,rest.yRot/DEG,rest.zRot/DEG);
            }
            return new BonePose(p.x,p.y,p.z,p.xRot/DEG,p.yRot/DEG,p.zRot/DEG);
        });
        p.x=(float)pose.position[0];p.y=(float)pose.position[1];p.z=(float)pose.position[2];
        p.xRot=(float)pose.rotation[0]*DEG;p.yRot=(float)pose.rotation[1]*DEG;p.zRot=(float)pose.rotation[2]*DEG;
        var scale=a.boneScales.computeIfAbsent(binding.name(),n->new Vector3f(p.xScale,p.yScale,p.zScale));
        p.xScale=scale.x;p.yScale=scale.y;p.zScale=scale.z;
    }
    public static void after(ModelPart p) {
        if(!SAVED.isEmpty() && SAVED.peek().part==p) SAVED.pop().restore();
    }
    public static void node(ModelPart p,PoseStack stack) {
        var frame=RenderContext.current(); if(frame==null) return;
        var b=RenderContext.binding(p); if(b==null) return;
        frame.partMatrices().put(p,new Matrix4f(stack.last().pose()));
        if(frame.shadow()) return;
        ActorState a=RenderContext.current().actor();
        ModelPart base=frame.adapter().skinBases.get(p);
        Matrix4f baseMatrix=base==null?null:frame.partMatrices().get(base);
        if(baseMatrix!=null) {
            float[] first=new float[16],second=new float[16];baseMatrix.get(first);stack.last().pose().get(second);
            double error=0;for(int i=0;i<16;i++) error=Math.max(error,Math.abs(first[i]-second[i]));
            a.skinTransformErrors.merge(b.name(),error,Math::max);
        }
        Matrix4f partMatrix=new Matrix4f(stack.last().pose()),handleMatrix=partMatrix;
        // Human head/torso handles sit on the face center and torso center; model pivots stay intact.
        if(frame.adapter().humanoid) {
            if(b.name().equals("head")) handleMatrix=new Matrix4f(partMatrix).translate(0,-4f/16,-4f/16);
            else if(b.name().equals("body")) handleMatrix=new Matrix4f(partMatrix).translate(0,6f/16,0);
        }
        a.nodes.putIfAbsent(b.name(),new ActorState.Node(b.name(),b.parent(),partMatrix,handleMatrix));
        if(b.playerLimb()) {
            String joint=joint(b.name()); if(joint==null) return;
            boolean arm=b.name().endsWith("arm"); float y=arm?4:6;
            BonePose pose=a.bones.get(joint);
            BendMath bend=new BendMath(pose.rotation,y,4);
            Vector3f pos=bend.deform(new Vector3f(0,y,0));
            Matrix4f matrix=new Matrix4f(stack.last().pose()).translate(pos.x/16,pos.y/16,pos.z/16).rotate(bend.orientation(y));
            a.nodes.putIfAbsent(joint,new ActorState.Node(joint,b.name(),matrix));
        }
    }
    public static String joint(String name) {
        return switch(name) { case "left_arm"->"left_elbow";case "right_arm"->"right_elbow";case "left_leg"->"left_knee";case "right_leg"->"right_knee"; default->null; };
    }
    public static void clear() { SAVED.clear(); }
}
