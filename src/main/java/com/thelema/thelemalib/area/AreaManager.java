package com.thelema.thelemalib.area;

import com.thelema.thelemalib.data.LevelMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber
public class AreaManager {
    static final String KEY = "thelemalib_area_manager";

    private AreaManager() {}
    /**添加一个区域，指定类型*/
    public static boolean add(ServerLevel level, String type, String name, BlockPos a, BlockPos b) {
        return add(level, type, name, posToAABB(a, b));
    }

    /**添加一个区域，指定类型, 传入AABB*/
    public static boolean add(ServerLevel level, String type, String name, AABB aabb) {
        AreaRegistry.AreaCreator creator = AreaRegistry.REGISTRY.get(type);
        if (creator == null) return false;
        Area area = creator.create(name, aabb);
        NeoForge.EVENT_BUS.register(area);
        map(level).put(name, area);
        return true;
    }

    /**添加一个区域，不使用类型，删除使用 area.name() 做 key*/
    public static boolean add(ServerLevel level, Area area){
        NeoForge.EVENT_BUS.register(area);
        map(level).put(area.name(), area);
        return true;
    }

    public static boolean remove(ServerLevel level, String name) {
        LevelMap<String, Area> map = map(level);
        Area area = map.remove(name);
        if (area == null) return false;
        NeoForge.EVENT_BUS.unregister(area);
        return true;
    }

    public static @Nullable Area get(ServerLevel level, String name){
        LevelMap<String, Area> map = map(level);
        return map.get(name);
    }

    public static AABB posToAABB(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX()) + 1;
        int maxY = Math.max(a.getY(), b.getY()) + 1;
        int maxZ = Math.max(a.getZ(), b.getZ()) + 1;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static LevelMap<String, Area> map(ServerLevel level) {
        return LevelMap.common(level, KEY);
    }

    @SubscribeEvent
    public static void reload(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (Area area : map(level).values()) {
                NeoForge.EVENT_BUS.register(area);
            }
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (Area area : map(level).values()) {
                NeoForge.EVENT_BUS.unregister(area);
            }
        }
    }
}
