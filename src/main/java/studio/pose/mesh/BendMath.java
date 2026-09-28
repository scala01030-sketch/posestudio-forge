package studio.pose.mesh;

import org.joml.*;
import java.lang.Math;


public final class BendMath {
    private final Quaternionf end;
    private final float start,width;
    private static final int STEPS=96;
    private final Vector3f[] centers=new Vector3f[STEPS+1];
    public BendMath(double[] degrees,float joint,float width) {

        this.angles=new Vector3f((float)Math.toRadians(degrees[0]),(float)Math.toRadians(degrees[1]),(float)Math.toRadians(degrees[2]));
        this.start=joint-width/2;this.width=width;
        end=rotation(1);
        centers[0]=new Vector3f(0,start,0);
        for(int i=1;i<=STEPS;i++) {
            Vector3f direction=rotation((i-.5f)/STEPS).transform(new Vector3f(0,width/STEPS,0));
            centers[i]=new Vector3f(centers[i-1]).add(direction);
        }
    }
    private final Vector3f angles;
    private Quaternionf rotation(float t) { return new Quaternionf().rotationZYX(angles.z*t,angles.y*t,angles.x*t); }
    public Quaternionf orientation(float y) { return rotation(Math.max(0,Math.min(1,(y-start)/width))); }
    public Vector3f center(float y) {
        if(y<=start) return new Vector3f(0,y,0);
        if(y>=start+width) return new Vector3f(centers[STEPS]).add(end.transform(new Vector3f(0,y-start-width,0)));
        float index=(y-start)/width*STEPS;int i=Math.min(STEPS-1,(int)index);
        return new Vector3f(centers[i]).lerp(centers[i+1],index-i);
    }
    public Vector3f deform(Vector3f v) { return center(v.y).add(orientation(v.y).transform(new Vector3f(v.x,0,v.z))); }
}
