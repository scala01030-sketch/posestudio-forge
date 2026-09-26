package studio.pose.serialization;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import studio.pose.client.ActorState;
import studio.pose.data.*;

public final class PoseSerializer {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static final class Document {
        public int version=1;
        public String entity;
        public ActorTransform actor;
        public Map<String,BonePose> bones=new LinkedHashMap<>();
    }
    private static Path file(Path directory,String name) throws IOException {
        if(name==null || !name.matches("[A-Za-z0-9_-]{1,64}")) throw new IOException("posestudio.error.pose_name");
        Path root=directory.toAbsolutePath().normalize(),result=root.resolve(name+".json").normalize();
        if(!result.startsWith(root)) throw new IOException("posestudio.error.pose_path");return result;
    }
    public static void save(Path directory,String name,String type,ActorState state) throws IOException {
        Document doc=new Document();doc.entity=type;doc.actor=state.transform;
        state.bones.forEach((key,value)->doc.bones.put(key,value.copy()));validate(doc);
        Files.createDirectories(directory);Path target=file(directory,name),temp=Files.createTempFile(directory,"pose-",".tmp");
        try {
            Files.writeString(temp,GSON.toJson(doc),StandardCharsets.UTF_8);
            try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public static Document load(Path directory,String name) throws IOException {
        Path path=file(directory,name);if(Files.size(path)>256*1024) throw new IOException("posestudio.error.pose_size");
        try { Document doc=GSON.fromJson(Files.readString(path,StandardCharsets.UTF_8),Document.class);validate(doc);return doc; }
        catch(JsonParseException | IllegalStateException e) { throw new IOException("posestudio.error.pose_json",e); }
    }
    public static void validate(Document d) throws IOException {
        if(d==null || d.version!=1 || d.entity==null || d.entity.length()>256 || d.actor==null || !d.actor.valid() || d.bones==null || d.bones.size()>512) throw new IOException("posestudio.error.pose_schema");
        for(var entry:d.bones.entrySet()) if(entry.getKey()==null || entry.getKey().isEmpty() || entry.getKey().length()>256 || entry.getValue()==null || !entry.getValue().valid()) throw new IOException("posestudio.error.pose_bone");
    }
    private PoseSerializer() {}
}
