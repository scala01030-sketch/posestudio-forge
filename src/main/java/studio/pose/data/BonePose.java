package studio.pose.data;


public final class BonePose {
    public double[] rotation = {0, 0, 0};
    public double[] position = {0, 0, 0};
    public BonePose() {}
    public BonePose(double x, double y, double z, double rx, double ry, double rz) {
        position = new double[]{x,y,z}; rotation = new double[]{rx,ry,rz};
    }
    public BonePose copy() { return new BonePose(position[0],position[1],position[2],rotation[0],rotation[1],rotation[2]); }
    public boolean valid() { return finite(rotation, 36000) && finite(position, 1024); }
    public static boolean finite(double[] values, double bound) {
        if (values == null || values.length != 3) return false;
        for (double v : values) if (!Double.isFinite(v) || Math.abs(v)>bound) return false;
        return true;
    }
}
