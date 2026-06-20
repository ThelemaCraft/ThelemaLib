// MapTag.java
package com.thelema.thelemalib.data.tool;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 继承 {@link AbstractMap} 的 Map 接口实现，底层直接操作 {@link CompoundTag}，零序列化开销。
 * <p>
 * 使用时就像普通 Map，但数据自动持久化到 NBT。支持所有已注册的类型作为键和值。
 * </p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public final class MapTag<K, V> extends AbstractMap<K, V> {
    private final CompoundTag tag;
    private final HolderLookup.Provider provider;
    // 懒加载 entrySet
    private transient Set<Entry<K, V>> entrySet;

    // 私有构造器，通过工厂方法创建
    private MapTag(CompoundTag tag, HolderLookup.Provider provider) {
        this.tag = tag;
        this.provider = provider;
    }

    /** 创建一个空的 MapTag */
    public static <K, V> MapTag<K, V> create(HolderLookup.Provider provider) {
        return new MapTag<>(new CompoundTag(), provider);
    }

    /** 包装已有的 CompoundTag */
    public static <K, V> MapTag<K, V> wrap(CompoundTag tag, HolderLookup.Provider provider) {
        return new MapTag<>(tag, provider);
    }

    /** 获取底层 CompoundTag（用于持久化） */
    public CompoundTag getTag() {
        return tag;
    }

    // ========== Map 接口实现 ==========

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        K k;
        try {
            k = (K) key;
        } catch (ClassCastException e) {
            return null;
        }
        String encodedKey = MapConverter.encodeKey(k, null);
        if (encodedKey == null || !tag.contains(encodedKey)) return null;
        Tag raw = tag.get(encodedKey);
        String type = MapConverter.getValueTypeId(encodedKey);
        return (V) MapConverter.decodeValue(raw, type, provider);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        String encodedKey = MapConverter.encodeKey(key, value);
        if (encodedKey == null) return null;
        Tag oldTag = tag.get(encodedKey);
        V oldValue = null;
        if (oldTag != null) {
            oldValue = (V) MapConverter.decodeValue(oldTag, MapConverter.getValueTypeId(encodedKey), provider);
        }
        tag.put(encodedKey, MapConverter.encodeValue(value, provider));
        return oldValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
        K k;
        try {
            k = (K) key;
        } catch (ClassCastException e) {
            return false;
        }
        String encodedKey = MapConverter.encodeKey(k, null);
        return encodedKey != null && tag.contains(encodedKey);
    }

    @Override
    public V remove(Object key) {
        // 不支持单独删除（可扩展，但暂无必要）
        throw new UnsupportedOperationException("Use clear() instead");
    }

    @Override
    public void clear() {
        tag.getAllKeys().clear();
    }

    @Override
    public int size() {
        return tag.size();
    }

    @Override
    @NotNull
    public Set<Entry<K, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new AbstractSet<>() {
                @Override
                public @NotNull Iterator<Entry<K, V>> iterator() {
                    Iterator<String> keyIterator = tag.getAllKeys().iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return keyIterator.hasNext();
                        }

                        @Override
                        @SuppressWarnings("unchecked")
                        public Entry<K, V> next() {
                            String rawKey = keyIterator.next();
                            K key = (K) MapConverter.decodeKey(rawKey);
                            Tag raw = tag.get(rawKey);
                            String type = MapConverter.getValueTypeId(rawKey);
                            V value = (V) MapConverter.decodeValue(raw, type, provider);
                            return new SimpleEntry<>(key, value);
                        }
                    };
                }

                @Override
                public int size() {
                    return tag.size();
                }
            };
        }
        return entrySet;
    }

    // ========== 扩展方法 ==========

    /**
     * 获取或创建子 MapTag。
     *
     * @param key 键
     * @return 子 MapTag，泛型为 {@code <Object, Object>}
     */
    public <NK, NV> MapTag<NK, NV> getOrCreate(K key) {
        String encodedKey = MapConverter.encodeKey(key, null);
        if (encodedKey == null) return null;
        if (tag.contains(encodedKey) && tag.get(encodedKey) instanceof CompoundTag ct) {
            return new MapTag<>(ct, provider);
        }
        CompoundTag child = new CompoundTag();
        tag.put(encodedKey, child);
        return new MapTag<>(child, provider);
    }

}