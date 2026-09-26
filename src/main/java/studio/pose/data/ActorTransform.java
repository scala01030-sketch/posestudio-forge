package studio.pose.data;

public record ActorTransform(double x, double y, double z, float yaw, float pitch, float roll) {
    public ActorTransform(double x,double y,double z,float yaw,float pitch) {this(x,y,z,yaw,pitch,0);}
    public boolean valid() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Math.abs(x) <= 29999984 && Math.abs(z) <= 29999984 && Math.abs(y) <= 2048
            && Float.isFinite(yaw) && Float.isFinite(pitch) && Float.isFinite(roll) && Math.abs(yaw)<=36000 && Math.abs(pitch)<=36000 && Math.abs(roll)<=36000;
    }
}
