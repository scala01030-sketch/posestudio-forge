package studio.pose.ui;

import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;


public final class StudioText {
    public static boolean internalBone(String path) {
        return Arrays.stream(path.split("/")).anyMatch(n->n.startsWith("EMF_") || n.startsWith("emf$"));
    }
    public static String leafBone(String path) {
        String leaf=path.substring(path.lastIndexOf('/')+1).replaceFirst("^EMF_","");
        String plain=leaf.replaceFirst("[0-9]+$","").replaceFirst("_rotation$","");
        return I18n.exists("posestudio.bone."+plain)?I18n.get("posestudio.bone."+plain):leaf;
    }
    public static String bone(String path) {
        return Arrays.stream(path.split("/",-1)).map(name -> {
            String key="posestudio.bone."+name;
            return I18n.exists(key)?I18n.get(key):name;
        }).collect(Collectors.joining(" / "));
    }
    public static Component fileError(Exception error) {
        String key=error.getMessage();
        Component detail=key!=null && key.startsWith("posestudio.")?Component.translatable(key):
            Component.translatable(error instanceof NoSuchFileException?"posestudio.error.file_missing":"posestudio.error.file_io");
        return Component.translatable("posestudio.error.file",detail);
    }
    private StudioText() {}
}
