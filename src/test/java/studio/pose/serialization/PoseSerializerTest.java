package studio.pose.serialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
import studio.pose.client.ActorState;
import studio.pose.data.*;
import static org.junit.jupiter.api.Assertions.*;
class PoseSerializerTest {
    @Test void loadsLegacyActorWithZeroRoll() throws Exception {
        Files.writeString(directory.resolve("legacy.json"),"{\"version\":1,\"entity\":\"minecraft:player\",\"actor\":{\"x\":0,\"y\":70,\"z\":0,\"yaw\":15,\"pitch\":10},\"bones\":{}}");
        assertEquals(0,PoseSerializer.load(directory,"legacy").actor.roll());
    }
    @Test void preservesWholeRotationAndRejectsNonFiniteRoll() throws Exception {
        var state=actor();state.transform=new ActorTransform(1,70,3,150,120,45);PoseSerializer.save(directory,"rotated","minecraft:player",state);assertEquals(state.transform,PoseSerializer.load(directory,"rotated").actor);
        state.transform=new ActorTransform(1,70,3,0,0,Float.NaN);assertThrows(IOException.class,()->PoseSerializer.save(directory,"invalid","minecraft:player",state));
    }
    @TempDir Path directory;
    private ActorState actor() { ActorState a=new ActorState(UUID.randomUUID(),new ActorTransform(1,70,3,30,0));a.bones.put("head",new BonePose(0,0,0,10,-15,0));return a; }
    @Test void savesAndLoadsDegreesAndActorTransform() throws Exception {
        PoseSerializer.save(directory,"example","minecraft:player",actor());var d=PoseSerializer.load(directory,"example");
        assertEquals(70,d.actor.y());assertEquals(-15,d.bones.get("head").rotation[1]);assertEquals("minecraft:player",d.entity);
    }
    @Test void rejectsTraversalNames() { assertThrows(IOException.class,()->PoseSerializer.save(directory,"../outside","minecraft:player",actor())); }
    @Test void rejectsNonFiniteBones() { var a=actor();a.bones.get("head").rotation[0]=Double.NaN;assertThrows(IOException.class,()->PoseSerializer.save(directory,"nan","minecraft:player",a)); }
    @Test void rejectsMalformedArrayAndSchema() throws Exception {
        Files.writeString(directory.resolve("bad.json"),"{\"version\":1,\"entity\":\"minecraft:player\",\"actor\":{\"x\":0,\"y\":70,\"z\":0,\"yaw\":0,\"pitch\":0},\"bones\":{\"head\":{\"rotation\":[1],\"position\":[0,0,0]}}}");
        assertThrows(IOException.class,()->PoseSerializer.load(directory,"bad"));
        Files.writeString(directory.resolve("bad.json"),"{\"version\":999}");assertThrows(IOException.class,()->PoseSerializer.load(directory,"bad"));
    }
    @Test void rejectsOversizedFile() throws Exception { Files.writeString(directory.resolve("big.json")," ".repeat(256*1024+1));assertThrows(IOException.class,()->PoseSerializer.load(directory,"big")); }
}
