// MapNBT.java
package com.thelema.thelemalib.data;

import com.mojang.serialization.Codec;
import com.thelema.thelemalib.data.tuple.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 带泛型的 Map 式 NBT 包装器，零序列化开销。
 * <p>
 * 直接操作底层 {@link CompoundTag}，写入时自动编码，读取时自动解码。
 * 支持所有基础类型、容器（MapNBT/List/Set/T2~T5）及 {@link ValueCodecRegistry} 中的自定义类型。
 * </p>
 *
 * @param <K> 键类型（String、数字或注册的可字符串化类型）
 * @param <V> 值类型
 */
public final class MapNBT<K, V> {
    private final CompoundTag tag;
    private final HolderLookup.Provider provider;

    private MapNBT(CompoundTag tag, HolderLookup.Provider provider) {
        this.tag = tag;
        this.provider = provider;
    }

    // ---------- 工厂方法 ----------

    /** 创建空 MapNBT，需提供 Provider */
    public static <K, V> MapNBT<K, V> create(HolderLookup.Provider provider) {
        return new MapNBT<>(new CompoundTag(), provider);
    }

    /** 包装已有 CompoundTag（通常从持久化数据恢复） */
    public static <K, V> MapNBT<K, V> wrap(CompoundTag tag, HolderLookup.Provider provider) {
        return new MapNBT<>(tag, provider);
    }

    // ---------- 写入 ----------

    /** 存入键值对 */
    public void put(@NotNull K key, @Nullable V value) {
        String encodedKey = encodeKey(key, value);
        if (encodedKey == null) return;
        tag.put(encodedKey, encodeValue(value));
    }

    /** 获取或创建嵌套 MapNBT */
    @SuppressWarnings("unchecked")
    public <NK, NV> MapNBT<NK, NV> getOrCreate(@NotNull K key) {
        String encodedKey = encodeKey(key, null);
        if (tag.contains(encodedKey) && tag.get(encodedKey) instanceof CompoundTag ct) {
            return new MapNBT<>(ct, provider);
        }
        CompoundTag child = new CompoundTag();
        tag.put(encodedKey, child);
        return new MapNBT<>(child, provider);
    }

    // ---------- 读取 ----------

    /** 读取值，返回 V 类型 */
    @Nullable
    @SuppressWarnings("unchecked")
    public V get(@NotNull K key) {
        String encodedKey = encodeKey(key, null);
        if (!tag.contains(encodedKey)) return null;
        Tag raw = tag.get(encodedKey);
        String type = extractValueType(encodedKey);
        return (V) decodeValue(raw, type);
    }

    /** 获取嵌套 MapNBT */
    @SuppressWarnings("unchecked")
    public <NK, NV> MapNBT<NK, NV> getMap(@NotNull K key) {
        String encodedKey = encodeKey(key, null);
        if (tag.contains(encodedKey) && tag.get(encodedKey) instanceof CompoundTag ct) {
            return new MapNBT<>(ct, provider);
        }
        return null;
    }

    // ---------- 元数据 ----------

    public boolean containsKey(@NotNull K key) {
        String encodedKey = encodeKey(key, null);
        return encodedKey != null && tag.contains(encodedKey);
    }

    public int size() {
        return tag.size();
    }

    public void clear() {
        tag.getAllKeys().clear();
    }

    /** 暴露底层 CompoundTag（用于持久化） */
    public CompoundTag getTag() {
        return tag;
    }

    // ========== 键编码 ==========

    private String encodeKey(K key, @Nullable V value) {
        // 1. 字符串键
        if (key instanceof String s && !s.isEmpty()) {
            String suffix = valueTypeSuffix(value);
            return suffix.isEmpty() ? s : s + "->" + suffix;
        }
        // 2. 基础数字键
        String numberType = numberTypeId(key);
        if (numberType != null) {
            String suffix = valueTypeSuffix(value);
            return suffix.isEmpty() ? numberType + "==" + key : numberType + "==" + key + "->" + suffix;
        }
        // 3. 自定义键（通过 KeyCodecRegistry）
        KeyCodecRegistry.Entry<?> entry = KeyCodecRegistry.getByClass(key.getClass());
        if (entry != null) {
            Tag t = entry.codec().encodeStart(NbtOps.INSTANCE, cast(key)).getOrThrow();
            if (t instanceof StringTag st) {
                String mid = entry.typeId() + "==" + st.getAsString();
                String suffix = valueTypeSuffix(value);
                return suffix.isEmpty() ? mid : mid + "->" + suffix;
            }
        }
        return null;
    }

    private String numberTypeId(Object key) {
        if (key instanceof Byte) return "byte";
        if (key instanceof Short) return "short";
        if (key instanceof Integer) return "int";
        if (key instanceof Long) return "long";
        if (key instanceof Float) return "float";
        if (key instanceof Double) return "double";
        return null;
    }

    // ========== 值类型后缀 ==========

    private String valueTypeSuffix(@Nullable Object value) {
        if (value == null) return "";
        if (value instanceof MapNBT) return "map";
        if (value instanceof List<?> l) return "list<" + inferElementType(l) + ">";
        if (value instanceof Set<?> s) return "set<" + inferElementType(s) + ">";
        if (value instanceof T2) return "t2";
        if (value instanceof T3) return "t3";
        if (value instanceof T4) return "t4";
        if (value instanceof T5) return "t5";
        ValueCodecRegistry.Entry<?> e = ValueCodecRegistry.getByClass(value.getClass());
        return e != null ? e.typeId() : "";
    }

    private String inferElementType(Collection<?> col) {
        for (Object o : col) {
            if (o == null) continue;
            if (o instanceof Byte) return "byte";
            if (o instanceof Short) return "short";
            if (o instanceof Integer) return "int";
            if (o instanceof Long) return "long";
            if (o instanceof Float) return "float";
            if (o instanceof Double) return "double";
            if (o instanceof String) return "string";
            if (o instanceof byte[]) return "byte[]";
            if (o instanceof int[]) return "int[]";
            if (o instanceof long[]) return "long[]";
            if (o instanceof MapNBT) return "map";
            if (o instanceof T2) return "t2";
            if (o instanceof T3) return "t3";
            if (o instanceof T4) return "t4";
            if (o instanceof T5) return "t5";
            ValueCodecRegistry.Entry<?> e = ValueCodecRegistry.getByClass(o.getClass());
            if (e != null) return e.typeId();
        }
        return "unknown";
    }

    // ========== 值编码 ==========

    private Tag encodeValue(@Nullable Object value) {
        if (value == null) return new CompoundTag(); // 占位
        // 基础类型
        if (value instanceof Byte b) return ByteTag.valueOf(b);
        if (value instanceof Short s) return ShortTag.valueOf(s);
        if (value instanceof Integer i) return IntTag.valueOf(i);
        if (value instanceof Long l) return LongTag.valueOf(l);
        if (value instanceof Float f) return FloatTag.valueOf(f);
        if (value instanceof Double d) return DoubleTag.valueOf(d);
        if (value instanceof String s) return StringTag.valueOf(s);
        if (value instanceof byte[] ba) return new ByteArrayTag(ba);
        if (value instanceof int[] ia) return new IntArrayTag(ia);
        if (value instanceof long[] la) return new LongArrayTag(la);

        // 容器
        if (value instanceof MapNBT<?,?> m) return m.tag;               // 直接复用其 CompoundTag
        if (value instanceof List<?> l) return encodeList(l);
        if (value instanceof Set<?> s) return encodeSet(s);
        if (value instanceof T2 t) return encodeT2(t);
        if (value instanceof T3 t) return encodeT3(t);
        if (value instanceof T4 t) return encodeT4(t);
        if (value instanceof T5 t) return encodeT5(t);

        // 自定义类型
        ValueCodecRegistry.Entry<?> e = ValueCodecRegistry.getByClass(value.getClass());
        if (e != null) {
            return e.codec().encodeStart(NbtOps.INSTANCE, cast(value)).getOrThrow();
        }
        return StringTag.valueOf(value.toString());
    }

    private Tag encodeList(List<?> list) {
        ListTag lt = new ListTag();
        for (Object o : list) lt.add(encodeValue(o));
        return lt;
    }

    private Tag encodeSet(Set<?> set) {
        ListTag lt = new ListTag();
        for (Object o : set) lt.add(encodeValue(o));
        return lt;
    }

    private Tag encodeT2(T2<?,?> t) {
        CompoundTag ct = new CompoundTag();
        ct.put("v1", encodeValue(t.v1));
        ct.put("v2", encodeValue(t.v2));
        return ct;
    }

    private Tag encodeT3(T3<?,?,?> t) {
        CompoundTag ct = new CompoundTag();
        ct.put("v1", encodeValue(t.v1));
        ct.put("v2", encodeValue(t.v2));
        ct.put("v3", encodeValue(t.v3));
        return ct;
    }

    private Tag encodeT4(T4<?,?,?,?> t) {
        CompoundTag ct = new CompoundTag();
        ct.put("v1", encodeValue(t.v1));
        ct.put("v2", encodeValue(t.v2));
        ct.put("v3", encodeValue(t.v3));
        ct.put("v4", encodeValue(t.v4));
        return ct;
    }

    private Tag encodeT5(T5<?,?,?,?,?> t) {
        CompoundTag ct = new CompoundTag();
        ct.put("v1", encodeValue(t.v1));
        ct.put("v2", encodeValue(t.v2));
        ct.put("v3", encodeValue(t.v3));
        ct.put("v4", encodeValue(t.v4));
        ct.put("v5", encodeValue(t.v5));
        return ct;
    }

    // ========== 值解码 ==========

    private Object decodeValue(Tag tag, String type) {
        if (type.isEmpty()) {
            // 基础类型
            if (tag instanceof ByteTag bt) return bt.getAsByte();
            if (tag instanceof ShortTag st) return st.getAsShort();
            if (tag instanceof IntTag it) return it.getAsInt();
            if (tag instanceof LongTag lt) return lt.getAsLong();
            if (tag instanceof FloatTag ft) return ft.getAsFloat();
            if (tag instanceof DoubleTag dt) return dt.getAsDouble();
            if (tag instanceof StringTag str) return str.getAsString();
            if (tag instanceof ByteArrayTag bat) return bat.getAsByteArray();
            if (tag instanceof IntArrayTag iat) return iat.getAsIntArray();
            if (tag instanceof LongArrayTag lat) return lat.getAsLongArray();
            if (tag instanceof CompoundTag ct) return new MapNBT<>(ct, provider); // 视为嵌套 MapNBT
            if (tag instanceof ListTag lt) return decodeList(lt, ""); // 无类型，元素按基础处理
            return tag.toString();
        }

        // 容器
        if ("map".equals(type) && tag instanceof CompoundTag ct) return new MapNBT<>(ct, provider);
        if (type.startsWith("list<") && tag instanceof ListTag lt) return decodeList(lt, extractElementType(type));
        if (type.startsWith("set<") && tag instanceof ListTag lt) return decodeSet(lt, extractElementType(type));
        if ("t2".equals(type) && tag instanceof CompoundTag ct) return decodeT2(ct);
        if ("t3".equals(type) && tag instanceof CompoundTag ct) return decodeT3(ct);
        if ("t4".equals(type) && tag instanceof CompoundTag ct) return decodeT4(ct);
        if ("t5".equals(type) && tag instanceof CompoundTag ct) return decodeT5(ct);

        // 自定义类型
        ValueCodecRegistry.Entry<?> e = ValueCodecRegistry.getById(type);
        if (e != null) {
            return e.codec().decode(NbtOps.INSTANCE, tag).getOrThrow().getFirst();
        }
        return tag.toString();
    }

    private List<Object> decodeList(ListTag list, String elementType) {
        List<Object> result = new ArrayList<>();
        for (Tag t : list) result.add(decodeValue(t, elementType));
        return result;
    }

    private Set<Object> decodeSet(ListTag list, String elementType) {
        Set<Object> result = new LinkedHashSet<>();
        for (Tag t : list) result.add(decodeValue(t, elementType));
        return result;
    }

    private T2<?,?> decodeT2(CompoundTag ct) {
        return new T2<>(decodeValue(ct.get("v1"), ""), decodeValue(ct.get("v2"), ""));
    }

    private T3<?,?,?> decodeT3(CompoundTag ct) {
        return new T3<>(decodeValue(ct.get("v1"), ""), decodeValue(ct.get("v2"), ""), decodeValue(ct.get("v3"), ""));
    }

    private T4<?,?,?,?> decodeT4(CompoundTag ct) {
        return new T4<>(decodeValue(ct.get("v1"), ""), decodeValue(ct.get("v2"), ""), decodeValue(ct.get("v3"), ""), decodeValue(ct.get("v4"), ""));
    }

    private T5<?,?,?,?,?> decodeT5(CompoundTag ct) {
        return new T5<>(decodeValue(ct.get("v1"), ""), decodeValue(ct.get("v2"), ""), decodeValue(ct.get("v3"), ""), decodeValue(ct.get("v4"), ""), decodeValue(ct.get("v5"), ""));
    }

    // ========== 辅助 ==========

    private String extractValueType(String encodedKey) {
        int idx = encodedKey.lastIndexOf("->");
        return idx >= 0 ? encodedKey.substring(idx + 2) : "";
    }

    private String extractElementType(String fullType) {
        int s = fullType.indexOf('<'), e = fullType.indexOf('>');
        return (s != -1 && e > s) ? fullType.substring(s + 1, e) : "";
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) { return (T) o; }
}