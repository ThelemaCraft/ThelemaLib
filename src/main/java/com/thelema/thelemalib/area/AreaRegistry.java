package com.thelema.thelemalib.area;

import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

public class AreaRegistry {

    public static final Map<String, AreaCreator> REGISTRY = new HashMap<>();

    public static String register(String type, AreaCreator creator) {
        REGISTRY.put(type, creator);
        return type;
    }

    public static Area create(String type, String name, AABB aabb) {
        AreaCreator creator = REGISTRY.get(type);
        if (creator == null) throw new IllegalArgumentException("Unknown area type: " + type);
        return creator.create(name, aabb);
    }

    public static void init() {
    }

    static {
    }

    @FunctionalInterface
    public interface AreaCreator {
        Area create(String name, AABB aabb);
    }
}
