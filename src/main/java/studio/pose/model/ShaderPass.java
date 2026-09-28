package studio.pose.model;

import java.lang.reflect.Method;


public final class ShaderPass {
    private static final Object API;
    private static final Method SHADOW;
    static {
        Object api=null; Method shadow=null;
        try {
            Class<?> type=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api=type.getMethod("getInstance").invoke(null);
            shadow=type.getMethod("isRenderingShadowPass");
        } catch(ReflectiveOperationException | LinkageError ignored) {}
        API=api; SHADOW=shadow;
    }
    public static boolean shadow() {
        if(SHADOW==null) return false;
        try { return Boolean.TRUE.equals(SHADOW.invoke(API)); }
        catch(ReflectiveOperationException ignored) { return false; }
    }
    private ShaderPass() {}
}
