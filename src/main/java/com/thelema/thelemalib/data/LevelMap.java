package com.thelema.thelemalib.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

public class LevelMap extends SavedData {
    // 直接使用 MapTag，它本身就是一个 Map<Object, Object>
    private final MapTag tag;

    // 私有构造函数，必须传入已构造的 MapTag
    private LevelMap(MapTag mapTag) {
        tag = mapTag;
    }

    /** 获取数据（返回标准 Map，实际为 MapTag） */
    public MapTag map() {
        return tag;
    }

    /** 在主世界 data 目录下创建文件 */
    public static LevelMap global(ServerLevel level, String file) {
        return common(level.getServer().overworld(), file);
    }

    /** 在维度 data 目录下创建文件 */
    public static LevelMap common(ServerLevel level, String file) {
        HolderLookup.Provider provider = level.registryAccess();
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(
                () -> new LevelMap(MapTag.create(provider)),          // 新建空 MapTag
                (nbt, p) -> new LevelMap(MapTag.wrap(nbt, p))       // 从 NBT 包装
            ),
            file
        );
    }

    /** 临时数据，不保存 */
    public static LevelMap temp(ServerLevel level) {
        return new LevelMap(MapTag.create(level.registryAccess()));
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        return this.tag.getTag();
    }
}