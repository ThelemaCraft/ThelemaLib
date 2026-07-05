package com.thelema.thelemalib.data.tool;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("rawtypes")
public class LevelMapClient {

    static final Map<String, Map<String, Map<Object, Object>>> CACHE = new ConcurrentHashMap<>();

    public static Map global(String file){
        return common(Level.OVERWORLD.location().toString(), file);
    }

    public static Map common(String dimId, String file){
        if (CACHE.containsKey(dimId)){
            Map<String, Map<Object, Object>> fileMap = CACHE.get(dimId);
            if (fileMap.containsKey(file)) {
                return fileMap.get(file);
            }
        }
        return null;

    }

    public static Map common(ResourceKey<Level> level, String file){
        return common(level.location().toString(), file);
    }

    public static void clear(){
        CACHE.clear();
    }

}
