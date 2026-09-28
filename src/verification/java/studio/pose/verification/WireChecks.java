package studio.pose.verification;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import studio.pose.data.ActorTransform;
import studio.pose.network.StudioNetwork;
import java.util.*;


public final class WireChecks {
    public static void verify() throws Exception {
        var encode=StudioNetwork.BatchRequest.class.getDeclaredMethod("encode",StudioNetwork.BatchRequest.class,FriendlyByteBuf.class);encode.setAccessible(true);
        var decode=StudioNetwork.BatchRequest.class.getDeclaredMethod("decode",FriendlyByteBuf.class);decode.setAccessible(true);
        var actors=new ArrayList<StudioNetwork.Entry>();for(int i=0;i<40;i++) actors.add(new StudioNetwork.Entry(UUID.randomUUID(),new ActorTransform(i,70,0,15,130,45)));
        var packet=new StudioNetwork.BatchRequest(false,actors);var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {encode.invoke(null,packet,buffer);if(!packet.equals(decode.invoke(null,buffer))) throw new AssertionError("40-entry rotation codec mismatch");}finally {buffer.release();}
        buffer=new FriendlyByteBuf(Unpooled.buffer());try {buffer.writeBoolean(true);buffer.writeVarInt(41);try {decode.invoke(null,buffer);throw new AssertionError("41-entry batch accepted");}catch(java.lang.reflect.InvocationTargetException error) {if(!(error.getCause() instanceof IllegalArgumentException)) throw error;}}finally {buffer.release();}
    }
}
