package studio.pose.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector3d;


public final class CameraController {
    public double x,y,z; public float pitch,yaw,roll; public double fov=70,speed=5;
    private long previous;
    public void begin(Camera camera) {
        var p=camera.getPosition(); x=p.x; y=p.y; z=p.z;
        pitch=camera.getXRot(); yaw=camera.getYRot(); roll=0;
        fov=Minecraft.getInstance().options.fov().get(); previous=System.nanoTime();
    }
    public void end() { previous=0; }
    public void look(double dx,double dy) {
        double sensitivity=Minecraft.getInstance().options.sensitivity().get();
        double v=Math.pow(sensitivity*.6+.2,3)*8*.15;
        yaw+=(float)(dx*v); pitch=Math.max(-90,Math.min(90,pitch+(float)(dy*v)));
    }
    public void lookAdjusted(double dx,double dy) {
        yaw+=(float)(dx*.15);pitch=Math.max(-90,Math.min(90,pitch+(float)(dy*.15)));
    }
    public void zoom(double steps) { fov=Math.max(10,Math.min(150,fov-steps*2)); }

    public void pan(double dx,double dy) {
        double a=Math.toRadians(yaw),p=Math.toRadians(pitch),r=Math.toRadians(roll);
        Vector3d right=new Vector3d(Math.cos(a),0,Math.sin(a));
        Vector3d up=new Vector3d(-Math.sin(a)*Math.sin(p),Math.cos(p),Math.cos(a)*Math.sin(p));
        Vector3d rolledRight=new Vector3d(right).mul(Math.cos(r)).add(new Vector3d(up).mul(Math.sin(r)));
        Vector3d rolledUp=new Vector3d(up).mul(Math.cos(r)).sub(new Vector3d(right).mul(Math.sin(r)));
        Vector3d move=rolledRight.mul(-dx).add(rolledUp.mul(dy)).mul(speed*.004*Math.tan(Math.toRadians(fov/2))/Math.tan(Math.toRadians(35)));
        x+=move.x;y+=move.y;z+=move.z;
    }
    public void frame() {
        long now=System.nanoTime(); double dt=previous==0?0:Math.min(.1,(now-previous)/1e9); previous=now;
        Minecraft mc=Minecraft.getInstance(); if(mc.screen!=null || !mc.isWindowActive()) return;
        long w=mc.getWindow().getWindow();
        double forward=(down(w,GLFW.GLFW_KEY_W)?1:0)-(down(w,GLFW.GLFW_KEY_S)?1:0);
        double side=(down(w,GLFW.GLFW_KEY_D)?1:0)-(down(w,GLFW.GLFW_KEY_A)?1:0);
        double up=(down(w,GLFW.GLFW_KEY_SPACE)?1:0)-(down(w,GLFW.GLFW_KEY_LEFT_SHIFT)?1:0);
        double angle=Math.toRadians(yaw);
        Vector3d v=new Vector3d(-Math.sin(angle)*forward+Math.cos(angle)*side,up,Math.cos(angle)*forward+Math.sin(angle)*side);
        if(v.lengthSquared()>0) v.normalize().mul(speed*dt*(down(w,GLFW.GLFW_KEY_LEFT_CONTROL)?4:1));
        x+=v.x; y+=v.y; z+=v.z;
        roll+=(float)(((down(w,GLFW.GLFW_KEY_E)?1:0)-(down(w,GLFW.GLFW_KEY_Q)?1:0))*45*dt);
        fov=Math.max(10,Math.min(150,fov+((down(w,GLFW.GLFW_KEY_RIGHT_BRACKET)?1:0)-(down(w,GLFW.GLFW_KEY_LEFT_BRACKET)?1:0))*40*dt));
    }
    private boolean down(long w,int key) { return GLFW.glfwGetKey(w,key)==GLFW.GLFW_PRESS; }
}
