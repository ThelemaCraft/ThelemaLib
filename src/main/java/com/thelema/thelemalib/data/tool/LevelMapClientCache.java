package com.thelema.thelemalib.data.tool;

import net.minecraft.nbt.CompoundTag;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LevelMapClientCache {
    private static final Map<String, CompoundTag> CACHE = new ConcurrentHashMap<>();

    public static void put(String file, CompoundTag tag) {
        CACHE.put(file, tag);
    }

    public static CompoundTag get(String file) {
        return CACHE.get(file);
    }

    public static void remove(String file) {
        CACHE.remove(file);
    }

    public static void clear() {
        CACHE.clear();
    }
}