package com.thelema.thelemalib.area;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;

public class AreaRegistry {

    public static final Map<String, AreaCreator> REGISTRY = new HashMap<>();

    public static final String TEST;

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
        TEST = register("test", (name, aabb) -> new Area() {
            @Override
            public String type() {
                return "test";
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public AABB aabb() {
                return aabb;
            }

            @SubscribeEvent
            public void init(BlockEvent.EntityPlaceEvent event) {
                BlockPos pos = event.getPos();
                Entity entity = event.getEntity();
                if (aabb.contains(pos.getCenter()) && entity instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.literal("这里不允许放方块！喵！"));
                }
            }
        });
    }

    @FunctionalInterface
    public interface AreaCreator {
        Area create(String name, AABB aabb);
    }
}
