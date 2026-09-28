package studio.pose.mesh;

import java.lang.reflect.*;
import java.util.*;
import org.joml.*;
import java.lang.Math;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.ModelPart;
import studio.pose.mixin.ModelPartAccess;
import studio.pose.model.*;


public final class PlayerLimbMesh {
    private record Vertex(Vector3f p,float u,float v) {
        Vertex lerp(Vertex b,float t) { return new Vertex(new Vector3f(p).lerp(b.p,t),u+(b.u-u)*t,v+(b.v-v)*t); }
    }
    private record Face(Vertex[] vertices,Vector3f normal) {}
    private static final Map<ModelPart.Cube,List<Face>> FACES=new WeakHashMap<>();
    private static final Set<ModelPart.Cube> UNSUPPORTED=Collections.newSetFromMap(new WeakHashMap<>());
    public static boolean render(ModelPart part,PoseStack.Pose matrix,VertexConsumer consumer,int light,int overlay,float r,float g,float b,float a) {
        var binding=RenderContext.binding(part);
        if(binding==null || !binding.playerLimb()) return false;
        String name=PoseController.joint(binding.name()); if(name==null) return false;
        var actor=RenderContext.current().actor(); var joint=actor.bones.get(name); if(joint==null) return false;

        if(Math.abs(joint.rotation[0])+Math.abs(joint.rotation[1])+Math.abs(joint.rotation[2])<.00001) return false;
        List<ModelPart.Cube> cubes=((ModelPartAccess)(Object)part).pose$cubes();
        List<List<Face>> faces=new ArrayList<>();
        for(var cube:cubes) {
            if(UNSUPPORTED.contains(cube)) return false;
            try { faces.add(FACES.computeIfAbsent(cube,PlayerLimbMesh::extract)); }
            catch(RuntimeException e) { UNSUPPORTED.add(cube); actor.adapterReady=false; actor.adapterStatus=net.minecraft.network.chat.Component.translatable("posestudio.adapter.mesh_unavailable",e.getClass().getSimpleName()); return false; }
        }
        BendMath bend=new BendMath(joint.rotation,binding.name().endsWith("arm")?4:6,4);
        String channel=(RenderContext.current().adapter().skinOverlays.contains(part)?"flat/":"base/")+binding.name();
        int startVertices=actor.bentVertices;
        for(var group:faces) for(Face face:group) {
            Vertex[] q=face.vertices;

            int first=-1;
            for(int i=0;i<4;i++) if(Math.abs(q[i].p.y-q[(i+1)%4].p.y)>.001) { first=i;break; }
            if(first<0) emit(q,bend,face.normal,matrix,consumer,light,overlay,r,g,b,a,channel);
            else {
                Vertex p0=q[first],p1=q[(first+1)%4],p2=q[(first+2)%4],p3=q[(first+3)%4];
                int segments=32;
                for(int i=0;i<segments;i++) {
                    float t0=(float)i/segments,t1=(float)(i+1)/segments;
                    emit(new Vertex[]{p0.lerp(p1,t0),p0.lerp(p1,t1),p3.lerp(p2,t1),p3.lerp(p2,t0)},bend,face.normal,matrix,consumer,light,overlay,r,g,b,a,channel);
                }
            }
        }
        if(!RenderContext.current().shadow() && RenderContext.current().adapter().skinOverlays.contains(part))
            actor.skinLayerVertices.merge("flat/"+binding.name(),actor.bentVertices-startVertices,Integer::sum);
        return true;
    }
    private static List<Face> extract(ModelPart.Cube cube) {
        try {
            Object[] polygons=(Object[])arrayField(cube.getClass()).get(cube);
            List<Face> result=new ArrayList<>();
            for(Object polygon:polygons) {
                Object[] raw=(Object[])arrayField(polygon.getClass()).get(polygon);
                Vector3f normal=new Vector3f((Vector3f)typedField(polygon.getClass(),Vector3f.class).get(polygon));
                Vertex[] vertices=new Vertex[4];
                if(raw.length!=4) throw new IllegalStateException("Unexpected cube face");
                for(int i=0;i<4;i++) {
                    Object vertex=raw[i]; List<Field> uv=new ArrayList<>();
                    for(Field f:vertex.getClass().getDeclaredFields()) if(f.getType()==float.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true);uv.add(f); }
                    if(uv.size()!=2) throw new IllegalStateException("Unexpected skin UV structure");
                    vertices[i]=new Vertex(new Vector3f((Vector3f)typedField(vertex.getClass(),Vector3f.class).get(vertex)),uv.get(0).getFloat(vertex),uv.get(1).getFloat(vertex));
                }
                result.add(new Face(vertices,normal));
            }
            return result;
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("Cannot inspect baked cube",e); }
    }
    private static Field arrayField(Class<?> c) {
        for(Field f:c.getDeclaredFields()) if(f.getType().isArray() && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true);return f; }
        throw new IllegalStateException("Missing polygon array");
    }
    private static Field typedField(Class<?> c,Class<?> t) {
        for(Field f:c.getDeclaredFields()) if(f.getType()==t && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true);return f; }
        throw new IllegalStateException("Missing vertex geometry");
    }
    private static void emit(Vertex[] q,BendMath bend,Vector3f normal,PoseStack.Pose matrix,VertexConsumer out,int light,int overlay,float r,float g,float b,float a,String channel) {
        for(Vertex v:q) {
            var frame=RenderContext.current();Vector3f p=bend.deform(v.p);
            if(frame!=null && !frame.shadow()) {frame.actor().bentVertices++;frame.actor().meshSignatures.merge(channel,(double)(p.x*31+p.y*17+p.z*7),Double::sum);}
            p.div(16);
            Vector3f n=bend.orientation(v.p.y).transform(new Vector3f(normal));
            out.vertex(matrix.pose(),p.x,p.y,p.z).color(r,g,b,a).uv(v.u,v.v).overlayCoords(overlay).uv2(light).normal(matrix.normal(),n.x,n.y,n.z).endVertex();
        }
    }
}
