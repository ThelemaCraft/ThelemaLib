package com.thelema.thelemalib.data;

import com.thelema.thelemalib.data.tool.MapConverter;
import com.thelema.thelemalib.data.tool.MapTag;
import com.thelema.thelemalib.data.tool.ServerSender;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"rawtypes", "unchecked"})
public class LevelMap extends SavedData {
    private final Map map;
    private final ServerLevel level;
    private final String file;

    private LevelMap(Map map, ServerLevel level, String file) {
        this.map = map;
        this.level = level;
        this.file = file;
    }

    /** 在主世界 data 目录下创建文件 */
    public static LevelMap global(ServerLevel level, String file) {
        return common(level.getServer().overworld(), file);
    }

    /** 在维度 data 目录下创建文件 */
    public static LevelMap common(ServerLevel level, String file) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(
                () -> new LevelMap(new HashMap<>(), level, file),          // 新建空 Map
                (nbt, p) -> new LevelMap(MapConverter.fromNbt(nbt, p), level, file)       // 从 NBT 反序列化
            ),
            file
        );
    }

    /** 临时数据，不保存 */
    public static LevelMap temp(ServerLevel level) {
        return new LevelMap(MapTag.create(level.registryAccess()), null, null);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        return MapConverter.toNBT(map, provider);
    }

    /** 获取数据（返回标准 Map，实际为 MapTag） */
    public Map map() {
        return map;
    }

    public ServerLevel level() {
        return level;
    }

    public String file() {
        return file;
    }
}