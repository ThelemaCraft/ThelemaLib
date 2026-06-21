// MapTag.java
package com.thelema.thelemalib.data.tool;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// 即时序列化成本高
/**
 * 继承 {@link AbstractMap} 的 Map 接口实现，底层直接操作 {@link CompoundTag}，零序列化开销。
 * <p>
 * 使用时就像普通 Map，但数据自动持久化到 NBT。支持所有已注册的类型作为键和值。
 * </p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
@Deprecated
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
        String noSuffix = MapConverter.encodeKey(k, null);
        if (noSuffix == null) return null;

        // 1. 精确匹配无后缀键
        if (tag.contains(noSuffix)) {
            Tag raw = tag.get(noSuffix);
            String type = MapConverter.getValueTypeId(noSuffix);
            return (V) MapConverter.decodeValue(raw, type, provider);
        }

        // 2. 遍历查找带后缀的键（noSuffix + "->"）
        for (String rawKey : tag.getAllKeys()) {
            if (rawKey.startsWith(noSuffix + "->")) {
                Tag raw = tag.get(rawKey);
                String type = MapConverter.getValueTypeId(rawKey);
                return (V) MapConverter.decodeValue(raw, type, provider);
            }
        }
        return null;
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
        String noSuffix = MapConverter.encodeKey(k, null);
        if (noSuffix == null) return false;
        if (tag.contains(noSuffix)) return true;
        for (String rawKey : tag.getAllKeys()) {
            if (rawKey.startsWith(noSuffix + "->")) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        V value = get(key);
        if (value == null) return null;
        K k;
        try {
            k = (K) key;
        } catch (ClassCastException e) {
            return null;
        }
        String noSuffix = MapConverter.encodeKey(k, null);
        if (noSuffix == null) return null;

        // 移除无后缀键
        tag.remove(noSuffix);
        // 移除所有带后缀的键
        List<String> toRemove = new ArrayList<>();
        for (String rawKey : tag.getAllKeys()) {
            if (rawKey.startsWith(noSuffix + "->")) {
                toRemove.add(rawKey);
            }
        }
        for (String rk : toRemove) {
            tag.remove(rk);
        }
        return value;
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
    public Set<K> keySet() {
        return new AbstractSet<>() {
            @Override
            public @NotNull Iterator<K> iterator() {
                // 复制当前所有原始键作为快照
                List<String> rawKeys = new ArrayList<>(tag.getAllKeys());
                return new Iterator<>() {
                    private int index = 0;
                    private K currentKey = null;  // 最近一次 next() 返回的键

                    @Override
                    public boolean hasNext() {
                        return index < rawKeys.size();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public K next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        String rawKey = rawKeys.get(index);
                        currentKey = (K) MapConverter.decodeKey(rawKey);
                        index++;
                        return currentKey;
                    }

                    @Override
                    public void remove() {
                        if (currentKey == null)
                            throw new IllegalStateException("next() must be called before remove()");

                        // 1. 调用 MapTag 的 remove，删除所有相关原始键
                        MapTag.this.remove(currentKey);

                        // 2. 从快照中删除该逻辑键对应的所有原始键
                        String noSuffix = MapConverter.encodeKey(currentKey, null);
                        if (noSuffix != null) {
                            Iterator<String> it = rawKeys.iterator();
                            while (it.hasNext()) {
                                String rk = it.next();
                                if (rk.equals(noSuffix) || rk.startsWith(noSuffix + "->")) {
                                    it.remove();
                                }
                            }
                        }

                        // 3. 调整索引：因为删除了当前元素（位于 index-1），后面的元素会前移
                        index = Math.max(0, index - 1);
                        currentKey = null;  // 防止重复 remove
                    }
                };
            }

            @Override
            public int size() {
                return tag.size();
            }

            @Override
            public boolean removeAll(Collection<?> c) {
                boolean modified = false;
                for (Object key : c) {
                    if (MapTag.this.containsKey(key)) {
                        MapTag.this.remove(key);
                        modified = true;
                    }
                }
                return modified;
            }

            @Override
            public void clear() {
                MapTag.this.clear();
            }
        };
    }

    @Override
    @NotNull
    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public @NotNull Iterator<Entry<K, V>> iterator() {
                // 复制当前所有原始键的快照
                List<String> rawKeys = new ArrayList<>(tag.getAllKeys());
                return new Iterator<>() {
                    private int index = 0;
                    private Entry<K, V> currentEntry = null;  // 最近一次 next() 返回的条目

                    @Override
                    public boolean hasNext() {
                        return index < rawKeys.size();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public Entry<K, V> next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        String rawKey = rawKeys.get(index);
                        K key = (K) MapConverter.decodeKey(rawKey);
                        Tag raw = tag.get(rawKey);
                        String type = MapConverter.getValueTypeId(rawKey);
                        V value = (V) MapConverter.decodeValue(raw, type, provider);
                        currentEntry = new SimpleEntry<>(key, value);
                        index++;
                        return currentEntry;
                    }

                    @Override
                    public void remove() {
                        if (currentEntry == null)
                            throw new IllegalStateException("next() must be called before remove()");
                        K key = currentEntry.getKey();
                        // 删除键对应的所有底层 NBT 条目
                        MapTag.this.remove(key);

                        // 从快照中移除该逻辑键对应的所有原始键
                        String noSuffix = MapConverter.encodeKey(key, null);
                        if (noSuffix != null) {
                            Iterator<String> it = rawKeys.iterator();
                            while (it.hasNext()) {
                                String rk = it.next();
                                if (rk.equals(noSuffix) || rk.startsWith(noSuffix + "->")) {
                                    it.remove();
                                }
                            }
                        }
                        // 调整索引，因为删除了当前元素（位于 index-1）
                        index = Math.max(0, index - 1);
                        currentEntry = null;  // 防止重复删除
                    }
                };
            }

            @Override
            public int size() {
                return tag.size();
            }

            @Override
            public boolean removeAll(Collection<?> c) {
                boolean modified = false;
                // 使用迭代器遍历当前所有条目（快照），避免递归
                Iterator<Entry<K, V>> it = iterator();
                while (it.hasNext()) {
                    Entry<K, V> entry = it.next();
                    if (c.contains(entry)) {
                        // 通过键删除
                        MapTag.this.remove(entry.getKey());
                        // 因为我们在迭代器遍历的同时修改了底层 Map，
                        // 但迭代器基于快照，所以不会受到影响，仍然安全。
                        // 注意：这里不能调用 it.remove()，因为迭代器的 remove 会基于快照删除，
                        // 但我们已经手动删除了，所以只需标记修改即可。
                        modified = true;
                    }
                }
                return modified;
            }

            @Override
            public void clear() {
                MapTag.this.clear();
            }
        };
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