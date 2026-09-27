package studio.pose.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import studio.pose.PoseStudio;
import studio.pose.data.ActorTransform;
import studio.pose.server.FreezeService;

public final class StudioNetwork {
    public static final int BEGIN=0, END=1, FREEZE=2, RELEASE=3, MOVE=4, PLACE=5, REMOVE=6;
    private static final String VERSION="5";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(PoseStudio.ID,"studio"),()->VERSION,VERSION::equals,VERSION::equals);
    public record Request(int action, UUID actor, ActorTransform transform) {
        static void encode(Request r,FriendlyByteBuf b) { b.writeVarInt(r.action); b.writeUUID(r.actor); ActorTransform t=r.transform; b.writeDouble(t.x());b.writeDouble(t.y());b.writeDouble(t.z());b.writeFloat(t.yaw());b.writeFloat(t.pitch());b.writeFloat(t.roll()); }
        static Request decode(FriendlyByteBuf b) { return new Request(b.readVarInt(),b.readUUID(),new ActorTransform(b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat(),b.readFloat())); }
        static void handle(Request r,Supplier<NetworkEvent.Context> s) {
            var c=s.get(); c.enqueueWork(()-> {
                ServerPlayer p=c.getSender(); if(p==null) return;
                String error=switch(r.action) {
                    case BEGIN -> FreezeService.begin(p);
                    case END -> { FreezeService.end(p.getUUID()); yield ""; }
                    case FREEZE -> FreezeService.acquire(p,r.actor);
                    case RELEASE -> { FreezeService.release(p,r.actor); yield ""; }
                    case MOVE -> FreezeService.move(p,r.actor,r.transform);
                    case REMOVE -> FreezeService.removePlaced(p,r.actor);
                    default -> "Unknown studio request.";
                };
                ActorTransform authoritative=FreezeService.transform(p,r.actor);
                CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Reply(r.action,r.actor,authoritative==null?r.transform:authoritative,error));
                if(r.action==FREEZE || r.action==RELEASE || r.action==REMOVE) syncProps(p);
            }); c.setPacketHandled(true);
        }
    }
    public record Reply(int action, UUID actor, ActorTransform transform, String error) {
        static void encode(Reply r,FriendlyByteBuf b) { Request.encode(new Request(r.action,r.actor,r.transform),b); b.writeUtf(r.error,512); }
        static Reply decode(FriendlyByteBuf b) { Request r=Request.decode(b); return new Reply(r.action,r.actor,r.transform,b.readUtf(512)); }
        static void handle(Reply r,Supplier<NetworkEvent.Context> s) {
            var c=s.get(); c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->studio.pose.client.StudioState.INSTANCE.reply(r))); c.setPacketHandled(true);
        }
    }
    public record PlaceRequest(studio.pose.data.PlacementSpec source, ActorTransform transform) {
        static void encode(PlaceRequest r,FriendlyByteBuf b) {
            b.writeEnum(r.source.kind());b.writeResourceLocation(r.source.type());b.writeUUID(r.source.source());b.writeNbt(r.source.stack().save(new net.minecraft.nbt.CompoundTag()));
            Request.encode(new Request(PLACE,new UUID(0,0),r.transform),b);
        }
        static PlaceRequest decode(FriendlyByteBuf b) {
            var kind=b.readEnum(studio.pose.data.PlacementSpec.Kind.class);var type=b.readResourceLocation();var source=b.readUUID();var tag=b.readNbt();var stack=tag==null?net.minecraft.world.item.ItemStack.EMPTY:net.minecraft.world.item.ItemStack.of(tag);
            return new PlaceRequest(new studio.pose.data.PlacementSpec(kind,type,stack,source),Request.decode(b).transform);
        }
        static void handle(PlaceRequest r,Supplier<NetworkEvent.Context> supplier) {
            var context=supplier.get();context.enqueueWork(()-> {
                var p=context.getSender();if(p==null) return;
                var result=FreezeService.place(p,r.source,r.transform);
                CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Reply(PLACE,result.actor(),r.transform,result.error()));
                syncProps(p);
            });context.setPacketHandled(true);
        }
    }
    public static void place(ResourceLocation type,ActorTransform transform) {place(studio.pose.data.PlacementSpec.entity(type),transform);}
    public static void place(studio.pose.data.PlacementSpec source,ActorTransform transform) { CHANNEL.sendToServer(new PlaceRequest(source,transform)); }
    public record Entry(UUID actor,ActorTransform transform) {}
    private static void entries(java.util.List<Entry> entries,FriendlyByteBuf b) {
        b.writeVarInt(entries.size());for(var entry:entries) Request.encode(new Request(MOVE,entry.actor,entry.transform),b);
    }
    private static java.util.List<Entry> entries(FriendlyByteBuf b) {
        int count=b.readVarInt();if(count<0 || count>studio.pose.StudioConfig.MAX_BATCH) throw new IllegalArgumentException("Invalid studio batch size");
        var result=new java.util.ArrayList<Entry>();for(int i=0;i<count;i++) {var r=Request.decode(b);result.add(new Entry(r.actor,r.transform));}return java.util.List.copyOf(result);
    }
    public record BatchRequest(boolean enter,java.util.List<Entry> actors) {
        static void encode(BatchRequest r,FriendlyByteBuf b) {b.writeBoolean(r.enter);entries(r.actors,b);}
        static BatchRequest decode(FriendlyByteBuf b) {return new BatchRequest(b.readBoolean(),entries(b));}
        static void handle(BatchRequest r,Supplier<NetworkEvent.Context> supplier) {
            var c=supplier.get();c.enqueueWork(()->{var p=c.getSender();if(p==null) return;
                String error=r.enter?FreezeService.enter(p,r.actors):FreezeService.moveBatch(p,r.actors);
                var authoritative=new java.util.ArrayList<Entry>();
                if(error.isEmpty()) for(var entry:r.actors) authoritative.add(new Entry(entry.actor,FreezeService.transform(p,entry.actor)));
                CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new BatchReply(r.enter,authoritative,error));
                if(r.enter && error.isEmpty()) syncProps(p);
            });c.setPacketHandled(true);
        }
    }
    public record BatchReply(boolean enter,java.util.List<Entry> actors,String error) {
        static void encode(BatchReply r,FriendlyByteBuf b) {b.writeBoolean(r.enter);entries(r.actors,b);b.writeUtf(r.error,512);}
        static BatchReply decode(FriendlyByteBuf b) {return new BatchReply(b.readBoolean(),entries(b),b.readUtf(512));}
        static void handle(BatchReply r,Supplier<NetworkEvent.Context> supplier) {var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->studio.pose.client.StudioState.INSTANCE.batchReply(r)));c.setPacketHandled(true);}
    }
    public static void batch(boolean enter,java.util.List<Entry> actors) {CHANNEL.sendToServer(new BatchRequest(enter,java.util.List.copyOf(actors)));}
    public record ScenePropsReply(java.util.List<Entry> actors) {
        static void encode(ScenePropsReply r,FriendlyByteBuf b) {entries(r.actors,b);}
        static ScenePropsReply decode(FriendlyByteBuf b) {return new ScenePropsReply(entries(b));}
        static void handle(ScenePropsReply r,Supplier<NetworkEvent.Context> supplier) {var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->studio.pose.client.StudioState.INSTANCE.sceneProps(r.actors)));c.setPacketHandled(true);}
    }
    private static void syncProps(ServerPlayer player) {CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new ScenePropsReply(FreezeService.placedEntries(player)));}
    public static void register() {
        CHANNEL.messageBuilder(ScenePropsReply.class,5,NetworkDirection.PLAY_TO_CLIENT).encoder(ScenePropsReply::encode).decoder(ScenePropsReply::decode).consumerMainThread(ScenePropsReply::handle).add();
        CHANNEL.messageBuilder(BatchRequest.class,3,NetworkDirection.PLAY_TO_SERVER).encoder(BatchRequest::encode).decoder(BatchRequest::decode).consumerMainThread(BatchRequest::handle).add();
        CHANNEL.messageBuilder(BatchReply.class,4,NetworkDirection.PLAY_TO_CLIENT).encoder(BatchReply::encode).decoder(BatchReply::decode).consumerMainThread(BatchReply::handle).add();
        CHANNEL.messageBuilder(PlaceRequest.class,2,NetworkDirection.PLAY_TO_SERVER).encoder(PlaceRequest::encode).decoder(PlaceRequest::decode).consumerMainThread(PlaceRequest::handle).add();
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::decode).consumerMainThread(Request::handle).add();
        CHANNEL.messageBuilder(Reply.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Reply::encode).decoder(Reply::decode).consumerMainThread(Reply::handle).add();
    }
    public static void send(int action,UUID actor,ActorTransform t) { CHANNEL.sendToServer(new Request(action,actor,t)); }
}
