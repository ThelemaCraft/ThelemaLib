package com.thelema.thelemalib.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 随世界保存的 Map
 * */
public class LevelMap extends SavedData {

    @SuppressWarnings("rawtypes")
    public Map map = new HashMap<>();

    /**在 save/world/data/中创建文件*/
    public static LevelMap global(ServerLevel level, String file){
        ServerLevel overworld = level.getServer().overworld();
        return common(overworld, file);
    }

    /**在 save/world/data/DIM/中创建文件*/
    public static LevelMap common(ServerLevel level, String file){

        return level.getDataStorage().computeIfAbsent(
                new Factory<>(LevelMap::new, (nbt, provider) -> {
                    LevelMap levelMap = new LevelMap();
                    levelMap.map = MapConverter.fromNbt(nbt, provider);
                    return levelMap;
                }),
                file
        );
    }

    // 临时 Map，不保存，不直接用 static，是为了隔绝存档
    public static LevelMap temp(ServerLevel level){
        ServerLevel overworld = level.getServer().overworld();
        // 只要没有，就创建新的，不会从文件中加载，也不保存
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(LevelMap::new, (nbt, provider) -> new LevelMap()),
                "temp_loticlast"
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        return MapConverter.toNBT(map, provider);
    }

}
