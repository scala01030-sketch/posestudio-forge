package studio.pose.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import studio.pose.client.StudioState;

/** Whole actor rotation wraps the original renderer, including renderers that ignore entity pitch/roll. */
public final class ActorOrientation {
    private ActorOrientation() {}
    public static org.joml.Quaternionf rotation(studio.pose.data.ActorTransform t) {return new org.joml.Quaternionf().rotationY((float)Math.toRadians(-t.yaw())).rotateX((float)Math.toRadians(t.pitch())).rotateZ((float)Math.toRadians(t.roll()));}
    public static net.minecraft.world.phys.Vec3 point(Entity e,net.minecraft.world.phys.Vec3 point,boolean inverse) {
        var s=StudioState.INSTANCE;var a=s.actors.get(e.getUUID());if(!s.active || a==null || !a.frozen) return point;
        var relative=point.subtract(e.position());var vector=new org.joml.Vector3f((float)relative.x,(float)relative.y,(float)relative.z);var q=rotation(a.transform);if(inverse) q.conjugate();q.transform(vector);return e.position().add(vector.x,vector.y,vector.z);
    }
    public static void render(Entity entity,PoseStack stack,Runnable original) {
        var s=StudioState.INSTANCE;var actor=s.actors.get(entity.getUUID());
        if(!s.active || actor==null || !actor.frozen) {original.run();return;}
        float yaw=entity.getYRot(),yawOld=entity.yRotO,pitch=entity.getXRot(),pitchOld=entity.xRotO;
        LivingEntity living=entity instanceof LivingEntity l?l:null;
        float body=living==null?0:living.yBodyRot,bodyOld=living==null?0:living.yBodyRotO,head=living==null?0:living.yHeadRot,headOld=living==null?0:living.yHeadRotO;
        stack.pushPose();
        try {
            // Let every native/mod renderer draw its zero-heading baseline once, then rotate that result.
            entity.setYRot(0);entity.yRotO=0;entity.setXRot(0);entity.xRotO=0;
            if(living!=null) {living.yBodyRot=living.yBodyRotO=living.yHeadRot=living.yHeadRotO=0;}
            var t=actor.transform;stack.mulPose(Axis.YP.rotationDegrees(-t.yaw()));stack.mulPose(Axis.XP.rotationDegrees(t.pitch()));stack.mulPose(Axis.ZP.rotationDegrees(t.roll()));
            original.run();
            if(!ShaderPass.shadow()) actor.lastMainRenderNanos=System.nanoTime();
        } finally {
            entity.setYRot(yaw);entity.yRotO=yawOld;entity.setXRot(pitch);entity.xRotO=pitchOld;
            if(living!=null) {living.yBodyRot=body;living.yBodyRotO=bodyOld;living.yHeadRot=head;living.yHeadRotO=headOld;}
            stack.popPose();
        }
    }
}
