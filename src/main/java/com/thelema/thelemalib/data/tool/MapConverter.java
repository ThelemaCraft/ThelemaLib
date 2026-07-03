package com.thelema.thelemalib.data.tool;

import com.mojang.serialization.Codec;
import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.data.registry.KeyRegistry;
import com.thelema.thelemalib.data.registry.ValueRegistry;
import com.thelema.thelemalib.data.tuple.T2;
import com.thelema.thelemalib.data.tuple.T3;
import com.thelema.thelemalib.data.tuple.T4;
import com.thelema.thelemalib.data.tuple.T5;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;

import java.util.*;

public final class MapConverter {

    // ---------- 公共 API ----------
    public static CompoundTag toNBT(Map<Object, Object> map, HolderLookup.Provider provider) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<Object, Object> e : map.entrySet()) {
            // 根据 key 和 value，编写 key
            String key = encodeKey(e.getKey(), e.getValue());
            // key 为 null，表明 key不认识，跳过
            if (key == null) {
                // key 类型，没有注册
                ThelemaLib.LOGGER.error("Unregistered Map's Key Type");
                continue;
            }
            // 编 value
            out.put(key, encodeValue(e.getValue(), provider));
        }
        return out;
    }

    public static Map<Object, Object> fromNBT(CompoundTag tag, HolderLookup.Provider provider) {
        Map<Object, Object> map = new HashMap<>();
        for (String rawKey : tag.getAllKeys()) {
            Object key = decodeKey(rawKey);
            if (key == null) continue;
            String valueTypeId = getValueTypeId(rawKey);
            map.put(key, decodeValue(tag.get(rawKey), valueTypeId, provider));
        }
        return map;
    }

    // ---------- 键名解析 ----------
    static String getValueTypeId(String rawKey) {
        int idx = rawKey.lastIndexOf("->");
        return idx >= 0 ? rawKey.substring(idx + 2) : "";
    }

    // 从值类型 ID 中提取 List/Set 的元素类型（如 "list<Bpos>" -> "Bpos"）
    static String extractElementType(String fullType) {
        int s = fullType.indexOf('<'), e = fullType.indexOf('>');
        return (s != -1 && e > s) ? fullType.substring(s + 1, e) : "";
    }

    // 编码 key
    static String encodeKey(Object key, Object value) {
        // Map 的 key是 String，直接就是 String，再分析 value类型
        if (key instanceof String s && !s.isEmpty()) {
            // String 合理，是至少一个字段的 字符串
            // 获取值类型的后缀
            String suffix = valueTypeSuffix(value);
            // 值不为空，便是正常的后缀，给s 加一个 -> map这样的东西
            return suffix.isEmpty() ? s : s + "->" + suffix;
        }
        // 基础数字类型直接编码，不走注册表
        if (key instanceof Number n) {
            String typeId;
            if (n instanceof Byte) typeId = "byte";
            else if (n instanceof Short) typeId = "short";
            else if (n instanceof Integer) typeId = "int";
            else if (n instanceof Long) typeId = "long";
            else if (n instanceof Float) typeId = "float";
            else if (n instanceof Double) typeId = "double";
            else typeId = null;

            if (typeId != null) {
                String data = n.toString();
                String suffix = valueTypeSuffix(value);
                return suffix.isEmpty() ? typeId + "==" + data : typeId + "==" + data + "->" + suffix;
            } else {
                ThelemaLib.LOGGER.error("encodeKey, key instanceof Number n, typeId == null");
            }
        }
        // key 特殊
        KeyRegistry.Entry<?> entry = KeyRegistry.getByClass(key.getClass());
        // 用 key类型 找到了对应的 codec
        if (entry != null) {
            // 把 key 变成了对应的 tag，都是 StringTag
            Tag tag = entry.codec().encodeStart(NbtOps.INSTANCE, cast(key)).getOrThrow();
            // 是 StringTag
            if (tag instanceof StringTag st) {
                // 这一步 int==1
                String mid = entry.typeId() + "==" + st.getAsString();
                // 值类型的后缀获取
                String suffix = valueTypeSuffix(value);
                // 不为空，追加： int==1->Bpos，这样子
                // 最终构建完毕
                return suffix.isEmpty() ? mid : mid + "->" + suffix;
            }
            // 不是StringTag？那不认识。
            ThelemaLib.LOGGER.error("encodeKey: 一个键，被编码为一个非String的Tag，导致不能转String");
        }
        // key 特殊还找不到，写了不认识的
        return null;
    }

    // 编码不带 value 后缀的 key
    public static String encodeKeyOnly(Object key) {
        if (key instanceof String s && !s.isEmpty()) return s;
        // 基础数字类型
        if (key instanceof Number n) {
            String typeId;
            if (n instanceof Byte) typeId = "byte";
            else if (n instanceof Short) typeId = "short";
            else if (n instanceof Integer) typeId = "int";
            else if (n instanceof Long) typeId = "long";
            else if (n instanceof Float) typeId = "float";
            else if (n instanceof Double) typeId = "double";
            else typeId = null;

            if (typeId != null) return typeId + "==" + n;
            return null;
        }

        KeyRegistry.Entry<?> entry = KeyRegistry.getByClass(key.getClass());
        if (entry != null) {
            Tag tag = entry.codec().encodeStart(NbtOps.INSTANCE, cast(key)).getOrThrow();
            if (tag instanceof StringTag st) return entry.typeId() + "==" + st.getAsString();
        }
        return null;
    }

    // 解码 key，string(type==data) 到实际对象
    static Object decodeKey(String raw) {
        String base = raw.contains("->") ? raw.substring(0, raw.lastIndexOf("->")) : raw; // 剥除值类型后缀
        int idx = base.indexOf("==");
        if (idx == -1) return base; // 无==为纯字符串
        String typeId = base.substring(0, idx); // 键类型标识
        String data = base.substring(idx + 2); // 键数据部分
        // 基础数字类型直接解码
        return switch (typeId) {
            case "byte" -> Byte.parseByte(data);
            case "short" -> Short.parseShort(data);
            case "int" -> Integer.parseInt(data);
            case "long" -> Long.parseLong(data);
            case "float" -> Float.parseFloat(data);
            case "double" -> Double.parseDouble(data);
            // 自定义类型走注册表
            default -> {
                KeyRegistry.Entry<?> entry = KeyRegistry.getById(typeId);
                yield entry != null ? entry.codec().decode(NbtOps.INSTANCE, StringTag.valueOf(data)).getOrThrow().getFirst() : null;
            }
        };
    }

    // 获取值类型的后缀
    public static String valueTypeSuffix(Object value) {
        // 空的，返回 空字符串
        if (value == null) return "";
        if (value instanceof Map) return "map";
        if (value instanceof List<?> l) return "list<" + inferElementType(l) + ">";
        if (value instanceof Set<?> s) return "set<" + inferElementType(s) + ">";
        if (value instanceof T2<?, ?>) return "t2";
        if (value instanceof T3<?, ?, ?>) return "t3";
        if (value instanceof T4<?, ?, ?, ?>) return "t4";
        if (value instanceof T5<?, ?, ?, ?, ?>) return "t5";

        // 特殊的，不包括容器
        ValueRegistry.CodecEntry<?> codec = ValueRegistry.getCodecByClass(value.getClass());
        if (codec != null) return codec.typeId();

        ValueRegistry.AdaptEntry<?> adapt = ValueRegistry.getAdaptByClass(value.getClass());
        if (adapt != null) return adapt.typeId();

        return "";
    }

    // 获取集合的类型
    static String inferElementType(Collection<?> col) {
        // 遍历，但是检测第一个就返回
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
            ValueRegistry.CodecEntry<?> e = ValueRegistry.getCodecByClass(o.getClass());
            if (e != null) return e.typeId();
            ValueRegistry.AdaptEntry<?> a = ValueRegistry.getAdaptByClass(o.getClass());
            if (a != null) return a.typeId();
        }
        // unknow 保底
        ThelemaLib.LOGGER.error("inferElementType: list<unknown> or set<unknown>");
        return "unknown";
    }

    // 值编码
    @SuppressWarnings("unchecked")
    public static Tag encodeValue(Object value, HolderLookup.Provider provider) {
        if (value == null) return new CompoundTag(); // 空占位
        // 基础类型，直接转为对应的 Tag
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

        // 容器，map 用 {}，list,set 用 []，tuple用
        if (value instanceof Map<?, ?> m) return encodeMap(m, provider);
        if (value instanceof List<?> l) return encodeList(l, provider);
        if (value instanceof Set<?> s) return encodeSet(s, provider);
        // 2-5，4个元组
        if (value instanceof T2<?, ?> t) return encodeT2(t, provider);
        if (value instanceof T3<?, ?, ?> t) return encodeT3(t, provider);
        if (value instanceof T4<?, ?, ?, ?> t) return encodeT4(t, provider);
        if (value instanceof T5<?, ?, ?, ?, ?> t) return encodeT5(t, provider);

        // 自定义类型，获取codec，转 Tag
        // 自定义类型：先查 Codec，再查 Adapt
        ValueRegistry.CodecEntry<?> codecEntry = ValueRegistry.getCodecByClass(value.getClass());
        if (codecEntry != null) {
            Codec<Object> codec = (Codec<Object>) codecEntry.codec();
            return codec.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow();
        }

        ValueRegistry.AdaptEntry<?> adaptEntry = ValueRegistry.getAdaptByClass(value.getClass());
        if (adaptEntry != null) {
            return adaptEntry.adapt().toTag(cast(value), provider);
        }

        // fallback
        return StringTag.valueOf(value.toString());
    }

    // 值解码
    public static Object decodeValue(Tag tag, String type, HolderLookup.Provider provider) {
        // type 为空 → 基础类型，直接映射
        if (type.isEmpty()) {
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
            return tag.toString();
        }

        // 容器类型
        if ("map".equals(type)) return decodeMap((CompoundTag) tag, provider);
        if (type.startsWith("list<")) return decodeList((ListTag) tag, extractElementType(type), provider);
        if (type.startsWith("set<")) return decodeSet((ListTag) tag, extractElementType(type), provider);
        if ("t2".equals(type)) return decodeT2((CompoundTag) tag, provider);
        if ("t3".equals(type)) return decodeT3((CompoundTag) tag, provider);
        if ("t4".equals(type)) return decodeT4((CompoundTag) tag, provider);
        if ("t5".equals(type)) return decodeT5((CompoundTag) tag, provider);

        // 自定义类型：先查 Codec，再查 Adapt
        ValueRegistry.CodecEntry<?> codecEntry = ValueRegistry.getCodecById(type);
        if (codecEntry != null) {
            return codecEntry.codec().decode(provider.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow().getFirst();
        }

        ValueRegistry.AdaptEntry<?> adapt = ValueRegistry.getAdaptById(type);
        if (adapt != null) {
            return adapt.adapt().fromTag(tag, provider);
        }

        // fallback
        return tag.toString();
    }

    // 容器编解码
    private static Tag encodeMap(Map<?, ?> map, HolderLookup.Provider provider) {
        CompoundTag ct = new CompoundTag();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            // 编码 key
            String k = encodeKey(e.getKey(), e.getValue());
            // 编码 value，并放入
            if (k != null) ct.put(k, encodeValue(e.getValue(), provider));
        }
        return ct;
    }

    private static Tag encodeList(List<?> list, HolderLookup.Provider provider) {
        ListTag lt = new ListTag();
        for (Object o : list) lt.add(encodeValue(o, provider));
        return lt; // 直接 ListTag，外层键名已标注类型
    }

    private static Tag encodeSet(Set<?> set, HolderLookup.Provider provider) {
        // 转换为 ListTag 存储，去重由 Set 自身保证
        ListTag lt = new ListTag();
        for (Object o : set) lt.add(encodeValue(o, provider));
        return lt;
    }

    // tuple 编码
    private static Tag encodeT2(T2<?, ?> t, HolderLookup.Provider p) {
        CompoundTag ct = new CompoundTag();
        ct.put("v1", encodeValue(t.v1, p));
        ct.put("v2", encodeValue(t.v2, p));
        return ct;
    }

    private static Tag encodeT3(T3<?, ?, ?> t, HolderLookup.Provider p) {
        CompoundTag ct = new CompoundTag();
        ct.put("v1", encodeValue(t.v1, p));
        ct.put("v2", encodeValue(t.v2, p));
        ct.put("v3", encodeValue(t.v3, p));
        return ct;
    }

    private static Tag encodeT4(T4<?, ?, ?, ?> t, HolderLookup.Provider p) {
        CompoundTag ct = new CompoundTag();
        ct.put("v1", encodeValue(t.v1, p));
        ct.put("v2", encodeValue(t.v2, p));
        ct.put("v3", encodeValue(t.v3, p));
        ct.put("v4", encodeValue(t.v4, p));
        return ct;
    }

    private static Tag encodeT5(T5<?, ?, ?, ?, ?> t, HolderLookup.Provider p) {
        CompoundTag ct = new CompoundTag();
        ct.put("v1", encodeValue(t.v1, p));
        ct.put("v2", encodeValue(t.v2, p));
        ct.put("v3", encodeValue(t.v3, p));
        ct.put("v4", encodeValue(t.v4, p));
        ct.put("v5", encodeValue(t.v5, p));
        return ct;
    }

    // 解码
    private static Map<Object, Object> decodeMap(CompoundTag ct, HolderLookup.Provider provider) {
        // 假设传入的是 顶层 Map的 NBT
        Map<Object, Object> map = new HashMap<>();
        // 有 key
        for (String rawKey : ct.getAllKeys()) {
            // key 转对象
            Object key = decodeKey(rawKey);
            if (key == null) continue;
            // 获取值信息
            String vt = getValueTypeId(rawKey);
            // 还原对象
            // 遇到新容器，进入递归
            map.put(key, decodeValue(ct.get(rawKey), vt, provider));
        }
        return map;
    }

    private static List<Object> decodeList(ListTag list, String type, HolderLookup.Provider provider) {
        List<Object> result = new ArrayList<>();
        for (Tag t : list) {
            // 元素类型由 elementTypeId 决定
            result.add(decodeValue(t, type, provider));
        }
        return result;
    }

    private static Set<Object> decodeSet(ListTag list, String elementTypeId, HolderLookup.Provider provider) {
        Set<Object> result = new LinkedHashSet<>();
        for (Tag t : list) {
            result.add(decodeValue(t, elementTypeId, provider));
        }
        return result;
    }

    // tuple 解码
    private static T2<?, ?> decodeT2(CompoundTag ct, HolderLookup.Provider p) {
        return new T2<>(decodeValue(ct.get("v1"), "", p), decodeValue(ct.get("v2"), "", p));
    }

    private static T3<?, ?, ?> decodeT3(CompoundTag ct, HolderLookup.Provider p) {
        return new T3<>(decodeValue(ct.get("v1"), "", p), decodeValue(ct.get("v2"), "", p), decodeValue(ct.get("v3"), "", p));
    }

    private static T4<?, ?, ?, ?> decodeT4(CompoundTag ct, HolderLookup.Provider p) {
        return new T4<>(decodeValue(ct.get("v1"), "", p), decodeValue(ct.get("v2"), "", p), decodeValue(ct.get("v3"), "", p), decodeValue(ct.get("v4"), "", p));
    }

    private static T5<?, ?, ?, ?, ?> decodeT5(CompoundTag ct, HolderLookup.Provider p) {
        return new T5<>(decodeValue(ct.get("v1"), "", p), decodeValue(ct.get("v2"), "", p), decodeValue(ct.get("v3"), "", p), decodeValue(ct.get("v4"), "", p), decodeValue(ct.get("v5"), "", p));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }
}