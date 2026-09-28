package studio.pose.mesh;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import studio.pose.model.PoseController;
import studio.pose.model.RenderContext;



public final class SkinLayerMesh {
    public static VertexConsumer wrap(ModelPart part,VertexConsumer original) {
        return wrap(part,original,false);
    }
    public static VertexConsumer wrapDescendant(ModelPart part,VertexConsumer original) {
        var frame=RenderContext.current();if(frame==null || frame.adapter()==null) return original;
        ModelPart root=frame.adapter().limbRoots.get(part);
        return root==null || root==part?original:wrap(root,original,true);
    }
    private static VertexConsumer wrap(ModelPart part,VertexConsumer original,boolean inherited) {
        var frame=RenderContext.current();var binding=RenderContext.binding(part);
        if(frame==null || binding==null || !binding.playerLimb()) return original;
        String joint=PoseController.joint(binding.name());
        var pose=frame.actor().bones.get(joint);var matrix=frame.partMatrices().get(part);
        if(pose==null || matrix==null || Math.abs(pose.rotation[0])+Math.abs(pose.rotation[1])+Math.abs(pose.rotation[2])<.00001) return original;
        String channel=inherited?(frame.adapter().skinOverlays.contains(part)?"flat/":"base/")+binding.name():binding.name();
        return new DeformingConsumer(original,new Matrix4f(matrix),new BendMath(pose.rotation,binding.name().endsWith("arm")?4:6,4),channel,frame,inherited);
    }
    private static final class Vertex {
        Vector3f p=new Vector3f(),n=new Vector3f();float r=1,g=1,b=1,a=1,u,v;int overlay,light;
        Vertex lerp(Vertex other,float t) {
            Vertex x=new Vertex();x.p.set(p).lerp(other.p,t);x.n.set(n).lerp(other.n,t);
            x.r=r+(other.r-r)*t;x.g=g+(other.g-g)*t;x.b=b+(other.b-b)*t;x.a=a+(other.a-a)*t;
            x.u=u+(other.u-u)*t;x.v=v+(other.v-v)*t;x.overlay=overlay;x.light=light;return x;
        }
    }
    private static final class DeformingConsumer implements VertexConsumer {
        final VertexConsumer out;final Matrix4f matrix,inverse;final Matrix3f normal,inverseNormal;
        final BendMath bend;final String limb;final RenderContext.Frame frame;
        final boolean inherited;
        final Vertex[] quad=new Vertex[4];int count;Vertex next=new Vertex();
        boolean defaultColor;int dr,dg,db,da;
        DeformingConsumer(VertexConsumer out,Matrix4f matrix,BendMath bend,String limb,RenderContext.Frame frame,boolean inherited) {
            this.out=out;this.matrix=matrix;inverse=new Matrix4f(matrix).invert();
            normal=matrix.normal(new Matrix3f());inverseNormal=new Matrix3f(normal).invert();
            this.bend=bend;this.limb=limb;this.frame=frame;
            this.inherited=inherited;
        }
        public VertexConsumer vertex(double x,double y,double z) { next.p.set((float)x,(float)y,(float)z);return this; }
        public VertexConsumer color(int r,int g,int b,int a) { next.r=r/255f;next.g=g/255f;next.b=b/255f;next.a=a/255f;return this; }
        public VertexConsumer uv(float u,float v) { next.u=u;next.v=v;return this; }
        public VertexConsumer overlayCoords(int u,int v) { next.overlay=u|(v<<16);return this; }
        public VertexConsumer uv2(int u,int v) { next.light=u|(v<<16);return this; }
        public VertexConsumer normal(float x,float y,float z) { next.n.set(x,y,z);return this; }
        public void defaultColor(int r,int g,int b,int a) { defaultColor=true;dr=r;dg=g;db=b;da=a; }
        public void unsetDefaultColor() { defaultColor=false; }
        public void endVertex() {
            if(defaultColor) color(dr,dg,db,da);
            inverse.transformPosition(next.p).mul(16);inverseNormal.transform(next.n).normalize();
            quad[count++]=next;next=new Vertex();
            if(count!=4) return;
            int edge=-1;
            for(int i=0;i<4;i++) if(Math.abs(quad[i].p.y-quad[(i+1)%4].p.y)>.001) { edge=i;break; }
            if(edge<0) emit(quad);
            else {
                Vertex p0=quad[edge],p1=quad[(edge+1)%4],p2=quad[(edge+2)%4],p3=quad[(edge+3)%4];
                int segments=Math.max(1,Math.min(32,(int)Math.ceil(Math.max(Math.abs(p0.p.y-p1.p.y),Math.abs(p2.p.y-p3.p.y))*8/3)));
                for(int i=0;i<segments;i++) {
                    float t0=(float)i/segments,t1=(float)(i+1)/segments;
                    emit(new Vertex[]{p0.lerp(p1,t0),p0.lerp(p1,t1),p3.lerp(p2,t1),p3.lerp(p2,t0)});
                }
            }
            count=0;
        }
        private void emit(Vertex[] vertices) {
            for(Vertex v:vertices) {
                Vector3f local=bend.deform(v.p);
                if(!frame.shadow()) frame.actor().meshSignatures.merge(limb,(double)(local.x*31+local.y*17+local.z*7),Double::sum);
                Vector3f p=matrix.transformPosition(local.div(16));
                Vector3f n=normal.transform(bend.orientation(v.p.y).transform(new Vector3f(v.n))).normalize();
                out.vertex(p.x,p.y,p.z,v.r,v.g,v.b,v.a,v.u,v.v,v.overlay,v.light,n.x,n.y,n.z);
            }
            if(!frame.shadow()) {
                frame.actor().skinLayerVertices.merge(limb,4,Integer::sum);
                if(inherited) frame.actor().bentVertices+=4;
            }
        }
    }
}
