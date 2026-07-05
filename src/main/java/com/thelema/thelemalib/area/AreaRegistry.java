package com.thelema.thelemalib.area;

import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class AreaRegistry {
    private static final Map<String, Supplier<Area>> FACTORIES = new HashMap<>();

    public static String register(String type, Supplier<Area> factory) {
        FACTORIES.put(type, factory);
        return type;
    }

    public static Area create(String type, String name, AABB aabb) {
        Supplier<Area> factory = FACTORIES.get(type);
        if (factory == null)
            throw new IllegalArgumentException("Unknown area type: " + type);
        Area area = factory.get();
        area.type = type;
        area.name = name;
        area.aabb = aabb;
        return area;
    }

    public static void init() {
    }
}