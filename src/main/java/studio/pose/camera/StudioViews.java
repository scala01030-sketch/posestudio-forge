package studio.pose.camera;

import studio.pose.client.StudioState;

/** Perspective presets relative to the actor's facing direction; world rendering is unchanged. */
public final class StudioViews {
    private StudioViews() {}
    public static void focus(int side) {
        var s=StudioState.INSTANCE;var a=s.actor();var e=a==null?null:s.entity(a.id);
        if(e==null) return;
        studio.pose.overlay.StudioOverlay.release();s.undo.remember(s.undo.cameraSnapshot());
        var center=studio.pose.model.ActorOrientation.point(e,e.getBoundingBox().getCenter(),false);var c=s.camera;
        c.yaw=a.transform.yaw()+switch(side) {case 0->180;case 1->0;case 2->90;default->-90;};
        c.pitch=0;c.roll=0;
        var window=net.minecraft.client.Minecraft.getInstance().getWindow();
        double viewportRatio=Math.max(.2,(double)(window.getGuiScaledWidth()-342)/window.getGuiScaledWidth());
        double radius=Math.max(e.getBbHeight(),e.getBbWidth())*.65;
        double distance=Math.max(2.5,radius/Math.tan(Math.toRadians(c.fov/2))/viewportRatio);
        double angle=Math.toRadians(c.yaw);c.x=center.x+Math.sin(angle)*distance;c.y=center.y;c.z=center.z-Math.cos(angle)*distance;
    }
}
