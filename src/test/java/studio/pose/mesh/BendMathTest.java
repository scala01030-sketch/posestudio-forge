package studio.pose.mesh;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
class BendMathTest {
    private void close(Vector3f expected,Vector3f actual,float epsilon) { assertEquals(expected.x,actual.x,epsilon);assertEquals(expected.y,actual.y,epsilon);assertEquals(expected.z,actual.z,epsilon); }
    @Test void straightPreservesAllSurfaceVertices() {
        BendMath bend=new BendMath(new double[]{0,0,0},6,4);
        for(float y=-2;y<=12;y+=.125) for(float x:new float[]{-2,2}) for(float z:new float[]{-2,2}) { Vector3f p=new Vector3f(x,y,z);close(p,bend.deform(p),.0001f); }
    }
    @Test void ninetyDegreeBendHasCircularCenterlineAndRigidLowerSegment() {
        BendMath bend=new BendMath(new double[]{90,0,0},6,4);
        float radius=8/(float)Math.PI;
        close(new Vector3f(0,4+radius,radius),bend.center(8),.001f);
        close(new Vector3f(0,4+radius,radius+4),bend.center(12),.001f);
        close(new Vector3f(0,0,1),bend.orientation(8).transform(new Vector3f(0,1,0)),.0001f);
    }
    @Test void surfacesAreContinuousAcrossBothBendBoundaries() {
        BendMath bend=new BendMath(new double[]{85,20,-15},6,4);
        for(float boundary:new float[]{4,8}) for(float x:new float[]{-2,2}) for(float z:new float[]{-2,2}) {
            assertTrue(bend.deform(new Vector3f(x,boundary-.0001f,z)).distance(bend.deform(new Vector3f(x,boundary+.0001f,z)))<.001);
        }
    }
    @Test void arbitraryAxesRemainFiniteAndCrossSectionsRetainWidth() {
        for(double[] angle:new double[][]{{0,90,0},{0,0,90},{160,-120,80},{-90,30,40}}) {
            BendMath bend=new BendMath(angle,4,4);
            for(float y=-2;y<=10;y+=.25) {
                Vector3f p=bend.deform(new Vector3f(-2,y,0)),q=bend.deform(new Vector3f(2,y,0));
                assertTrue(p.isFinite());assertEquals(4,p.distance(q),.0001);
            }
        }
    }
    @Test void overlayThicknessTracksTheSameBentCrossSectionForClassicAndSlimArms() {
        BendMath bend=new BendMath(new double[]{-100,25,15},4,4);
        for(float edge:new float[]{1.5f,2}) for(float y=-1;y<=12;y+=.125f) {
            Vector3f base=new Vector3f(edge,y,2),outer=new Vector3f(edge+.25f,y,2.25f);
            Vector3f separation=bend.deform(outer).sub(bend.deform(base));
            close(bend.orientation(y).transform(new Vector3f(.25f,0,.25f)),separation,.00001f);
        }
    }
}
