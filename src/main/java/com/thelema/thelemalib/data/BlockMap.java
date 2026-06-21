package com.thelema.thelemalib.data;

import com.thelema.thelemalib.data.tool.MapTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

/**
 * 基于 BlockPos 的持久化 Map，复用 LevelMap。
 *
 * @param <T> 值类型
 */
@SuppressWarnings("unchecked")
public class BlockMap<T> {
    private final LevelMap lm;

    private BlockMap(LevelMap lm) {
        this.lm = lm;
    }

    public static <T> BlockMap<T> get(ServerLevel level, String file) {
        return new BlockMap<>(LevelMap.common(level, file));
    }

    public static <T> BlockMap<T> temp(ServerLevel level) {
        return new BlockMap<>(LevelMap.temp(level));
    }

    // ========== 便捷委托 ==========

    public T put(BlockPos key, T value) {
        return (T) lm.map().put(key, value);
    }

    public T get(BlockPos key) {
        return (T) lm.map().get(key);
    }

    public boolean containsKey(BlockPos key) {
        return lm.map().containsKey(key);
    }

    public T remove(BlockPos key) {
        return (T) lm.map().remove(key);
    }

    public int size() {
        return lm.map().size();
    }

    public boolean isEmpty() {
        return lm.map().isEmpty();
    }

    public void clear() {
        lm.map().clear();
    }

    public void setDirty() {
        lm.setDirty();
    }

    public Map<BlockPos, T> map(){
        return lm.map();
    }

    public ServerLevel level(){
        return lm.level();
    }

    public String file(){
        return lm.file();
    }

}