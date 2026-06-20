package com.thelema.thelemalib.data;

import com.thelema.thelemalib.data.tool.MapTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 基于 BlockPos 的持久化 Map，复用 LevelMap。
 *
 * @param <T> 值类型
 */
public class BlockMap<T> {
    private final LevelMap lm;

    private BlockMap(LevelMap lm) {
        this.lm = lm;
    }

    public static <T> BlockMap<T> common(ServerLevel level, String file) {
        return new BlockMap<>(LevelMap.common(level, file));
    }

    public static <T> BlockMap<T> global(ServerLevel level, String file) {
        return new BlockMap<>(LevelMap.global(level, file));
    }

    public static <T> BlockMap<T> temp(ServerLevel level) {
        return new BlockMap<>(LevelMap.temp(level));
    }

    /** 获取底层 MapTag */
    @SuppressWarnings("unchecked")
    public MapTag<BlockPos, T> map() {
        return lm.map();
    }

    // ========== 便捷委托 ==========

    public T put(BlockPos key, T value) {
        return map().put(key, value);
    }

    public T get(BlockPos key) {
        return map().get(key);
    }

    public boolean containsKey(BlockPos key) {
        return map().containsKey(key);
    }

    public T remove(BlockPos key) {
        return map().remove(key);
    }

    public int size() {
        return map().size();
    }

    public boolean isEmpty() {
        return map().isEmpty();
    }

    public void clear() {
        map().clear();
    }

    public void setDirty() {
        lm.setDirty();
    }

    public void sync(){
        lm.sync();
    }
}