package com.thelema.thelemalib.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Map ↔ NBT 双向转换器。
 *
 * <p><b>键名解析规则：</b>
 * <ol>
 *   <li>按 {@code ->} 分割，右侧是 <b>值类型标识</b>（可为空）。</li>
 *   <li>左侧再按 {@code ==} 分割：
 *       <ul>
 *         <li>若存在 {@code ==}，左侧是 <b>键类型标识</b>，右侧是 <b>键数据</b>。</li>
 *         <li>否则整个左侧是纯字符串键名。</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p><b>基础类型（内聚处理，不走注册表）：</b>
 * Byte, Short, Int, Long, Float, Double, String, byte[], int[], long[] 及容器 Map/List/Set/T2~T7。
 * 所有基础类型作为 Value 时不标注类型；作为 Key 时必须通过 {@code 类型==数据} 标注。
 *
 * <p><b>自定义类型（通过 {@link CodecRegister} 注册）：</b>
 * 使用字符串 Codec 作为 Value 时存储为字符串，键名中标注 {@code ->类型Id}；
 * 使用 CompoundTag Codec 作为 Value 时存储为 CompoundTag，键名中标注 {@code ->类型Id}。
 */
public final class MapHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapHelper.class);

    // ---------- 公共 API ----------
    public static CompoundTag toNBT(Map<Object, Object> map, HolderLookup.Provider provider) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<Object, Object> e : map.entrySet()) {
            String key = encodeKey(e.getKey(), e.getValue());
            if (key == null) {
                LOGGER.warn("Skipping unsupported key: {}", e.getKey());
                continue;
            }
            out.put(key, encodeValue(e.getValue(), provider));
        }
        return out;
    }

    public static Map<Object, Object> fromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        Map<Object, Object> map = new HashMap<>();
        for (String rawKey : tag.getAllKeys()) {
            Object key = decodeKey(rawKey);
            if (key == null) {
                LOGGER.warn("Skipping unknown key format: {}", rawKey);
                continue;
            }
            String valueTypeId = getValueTypeId(rawKey);
            String elementTypeId = getElementTypeId(rawKey);
            map.put(key, decodeValue(tag.get(rawKey), valueTypeId, elementTypeId, provider));
        }
        return map;
    }

    // ---------- 键名解析 ----------
    /** 从完整键名中提取值类型标识（{@code ->} 右侧内容） */
    private static String getValueTypeId(String rawKey) {
        int idx = rawKey.lastIndexOf("->");
        return idx >= 0 ? rawKey.substring(idx + 2) : "";
    }

    // 从键名中提取 List/Set 的元素类型（如 "list<Bpos>" -> "Bpos"）
    private static String getElementTypeId(String rawKey) {
        String valuePart = rawKey.contains("->") ? rawKey.substring(rawKey.lastIndexOf("->") + 2) : rawKey;
        int start = valuePart.indexOf('<');
        int end = valuePart.indexOf('>');
        if (start != -1 && end > start) {
            return valuePart.substring(start + 1, end);
        }
        return "";
    }

    /** 从完整键名中提取键类型标识和键数据（{@code ==} 分割），若没有则返回 null */
    private static String[] splitKeyTypeData(String rawKey) {
        String base = rawKey.contains("->") ? rawKey.substring(0, rawKey.lastIndexOf("->")) : rawKey;
        int idx = base.indexOf("==");
        if (idx >= 0) {
            return new String[]{base.substring(0, idx), base.substring(idx + 2)};
        }
        return null;
    }

    // ---------- 键编解码 ----------
    private static String encodeKey(Object key, Object value) {
        // 1. 纯字符串 Key
        if (key instanceof String s) {
            String suffix = getValueTypeSuffix(value);
            return suffix.isEmpty() ? s : s + "->" + suffix;
        }

        // 2. 基础类型 Key（通过 BaseType 枚举查找）
        BaseType base = BaseType.byClass(key.getClass());
        if (base != null) {
            String data = base.encodeToString(key);
            if (data == null) return null; // 理论上不会
            String suffix = getValueTypeSuffix(value);
            return suffix.isEmpty() ? base.typeId + "==" + data : base.typeId + "==" + data + "->" + suffix;
        }

        // 3. 自定义类型 Key（必须通过注册表）
        CodecRegister.Entry<?> entry = CodecRegister.getByClass(key.getClass());
        if (entry != null) {
            // 尝试用 Codec 编码为 StringTag
            Tag tag = entry.codec().encodeStart(NbtOps.INSTANCE, cast(key))
                    .getOrThrow(msg -> new RuntimeException("Encoding key failed: " + msg));
            if (tag instanceof StringTag st) {
                String suffix = getValueTypeSuffix(value);
                String mid = entry.typeId() + "==" + st.getAsString();
                return suffix.isEmpty() ? mid : mid + "->" + suffix;
            }
            LOGGER.warn("Codec for {} did not produce StringTag, cannot be used as key", entry.typeId());
            return null;
        }

        LOGGER.warn("No codec registered for key type: {}", key.getClass().getName());
        return null;
    }

    private static Object decodeKey(String rawKey) {
        if (rawKey == null) return null;
        // 移除值类型后缀
        String base = rawKey.contains("->") ? rawKey.substring(0, rawKey.lastIndexOf("->")) : rawKey;

        // 检查是否有键类型标注
        int idx = base.indexOf("==");
        if (idx >= 0) {
            String typeId = base.substring(0, idx);
            String data = base.substring(idx + 2);

            // 先查基础类型
            BaseType baseType = BaseType.byId(typeId);
            if (baseType != null) {
                return baseType.decodeFromString(data);
            }
            // 再查自定义注册表
            CodecRegister.Entry<?> entry = CodecRegister.getById(typeId);
            if (entry != null) {
                return entry.codec().decode(NbtOps.INSTANCE, StringTag.valueOf(data))
                        .getOrThrow(msg -> new RuntimeException("Decoding key failed: " + msg))
                        .getFirst();
            }
            LOGGER.warn("Unknown typeId in key: {}", typeId);
            return null;
        }

        // 纯字符串 Key
        return base;
    }

    // ---------- 值类型后缀生成 ----------
    /** 根据值对象，生成应附加在键名后的 {@code ->类型} 后缀 */
    private static String getValueTypeSuffix(Object value) {
        if (value == null) return "";
        // 基础类型（包括数字、String、数组、容器）不标注
        if (BaseType.byClass(value.getClass()) != null) return "";
        if (value instanceof Map || value instanceof List || value instanceof Set || isTuple(value)) return "";

        // 自定义类型：必须标注
        CodecRegister.Entry<?> entry = CodecRegister.getByClass(value.getClass());
        if (entry != null) return entry.typeId();

        // 未注册类型，不标注（解码时会变成字符串）
        return "";
    }

    // ---------- 值编解码 ----------
    private static Tag encodeValue(Object value, HolderLookup.Provider provider) {
        if (value == null) return new CompoundTag(); // 用空 CompoundTag 表示 null

        // 基础数字类型
        if (value instanceof Byte b) return ByteTag.valueOf(b);
        if (value instanceof Short s) return ShortTag.valueOf(s);
        if (value instanceof Integer i) return IntTag.valueOf(i);
        if (value instanceof Long l) return LongTag.valueOf(l);
        if (value instanceof Float f) return FloatTag.valueOf(f);
        if (value instanceof Double d) return DoubleTag.valueOf(d);
        if (value instanceof String s) return StringTag.valueOf(s);

        // 基础数组类型
        if (value instanceof byte[] ba) return new ByteArrayTag(ba);
        if (value instanceof int[] ia) return new IntArrayTag(ia);
        if (value instanceof long[] la) return new LongArrayTag(la);

        // 容器类型
        if (value instanceof Map<?,?> m) return encodeMap(m, provider);
        if (value instanceof List<?> l) return encodeCollection(l, "list", provider);
        if (value instanceof Set<?> s)  return encodeCollection(new ArrayList<>(s), "set", provider);
        if (isTuple(value))            return encodeTuple(value, provider);

        // 自定义类型（通过 CodecRegister）
        CodecRegister.Entry<?> entry = CodecRegister.getByClass(value.getClass());
        if (entry != null) {
            return entry.codec().encodeStart(NbtOps.INSTANCE, cast(value))
                    .getOrThrow(msg -> new RuntimeException("Encoding value failed: " + msg));
        }

        // fallback：未注册类型 -> toString()
        LOGGER.warn("Unregistered type {} used as value, storing as String", value.getClass().getName());
        return StringTag.valueOf(value.toString());
    }

    private static Object decodeValue(Tag tag, String valueTypeId, String elementTypeId, HolderLookup.Provider provider) {
        if (tag instanceof CompoundTag ct) {
            // 判断是否是容器包装（有 $type 字段）
            if (ct.contains("$type")) {
                String containerType = ct.getString("$type");
                return switch (containerType) {
                    case "map"    -> decodeMap(ct, provider);
                    case "list"   -> decodeCollection(ct, elementTypeId, provider);
                    case "set"    -> decodeCollection(ct, elementTypeId, provider);
                    case "tuple"  -> decodeTuple(ct, provider);
                    default -> {
                        // 可能是自定义大对象（如 itemstack、nbt）
                        CodecRegister.Entry<?> entry = CodecRegister.getById(containerType);
                        if (entry != null) {
                            yield entry.codec().decode(NbtOps.INSTANCE, ct)
                                    .getOrThrow(msg -> new RuntimeException("Decoding value failed: " + msg))
                                    .getFirst();
                        }
                        LOGGER.warn("Unknown container $type: {}, treating as map", containerType);
                        yield decodeMap(ct, provider);
                    }
                };
            }

            // 没有 $type 的 CompoundTag，可能是直接编码的自定义大对象
            // 如果值类型标注了，尝试用该类型解码
            if (!valueTypeId.isEmpty()) {
                CodecRegister.Entry<?> entry = CodecRegister.getById(valueTypeId);
                if (entry != null) {
                    return entry.codec().decode(NbtOps.INSTANCE, ct)
                            .getOrThrow(msg -> new RuntimeException("Decoding value failed: " + msg))
                            .getFirst();
                }
            }
            // 否则当作未注册的自定义对象？直接返回 CompoundTag 本身
            return ct;
        }

        // 基础 Tag 类型直接还原
        if (tag instanceof ByteTag bt) return bt.getAsByte();
        if (tag instanceof ShortTag st) return st.getAsShort();
        if (tag instanceof IntTag it) return it.getAsInt();
        if (tag instanceof LongTag lt) return lt.getAsLong();
        if (tag instanceof FloatTag ft) return ft.getAsFloat();
        if (tag instanceof DoubleTag dt) return dt.getAsDouble();
        if (tag instanceof StringTag str) {
            String s = str.getAsString();
            // 如果值类型标注了，尝试用该类型解码（即使它本是字符串）
            if (!valueTypeId.isEmpty() && !BaseType.isBaseType(valueTypeId)) {
                CodecRegister.Entry<?> entry = CodecRegister.getById(valueTypeId);
                if (entry != null) {
                    return entry.codec().decode(NbtOps.INSTANCE, StringTag.valueOf(s))
                            .getOrThrow(msg -> new RuntimeException("Decoding value failed: " + msg))
                            .getFirst();
                }
            }
            return s;
        }
        if (tag instanceof ByteArrayTag bat) return bat.getAsByteArray();
        if (tag instanceof IntArrayTag iat) return iat.getAsIntArray();
        if (tag instanceof LongArrayTag lat) return lat.getAsLongArray();
        if (tag instanceof ListTag lt) {
            // 没有 $type 的 ListTag，可能是基础的 ListTag（极少），返回原 Tag
            return lt;
        }

        return tag.toString();
    }

    // ---------- 容器编码 ----------
    private static Tag encodeMap(Map<?,?> map, HolderLookup.Provider provider) {
        CompoundTag ct = new CompoundTag();
        ct.putString("$type", "map");
        for (Map.Entry<?,?> e : map.entrySet()) {
            String key = encodeKey(e.getKey(), e.getValue());
            if (key != null) {
                ct.put(key, encodeValue(e.getValue(), provider));
            }
        }
        return ct;
    }

    private static Tag encodeCollection(Collection<?> col, String type, HolderLookup.Provider provider) {
        CompoundTag ct = new CompoundTag();
        ct.putString("$type", type);
        ListTag list = new ListTag();
        for (Object obj : col) {
            list.add(encodeValue(obj, provider));
        }
        ct.put("list", list);
        return ct;
    }

    private static Tag encodeTuple(Object tuple, HolderLookup.Provider provider) {
        CompoundTag ct = new CompoundTag();
        ct.putString("$type", "tuple");
        // T2~T7 字段名为 v1...vN
        int size = getTupleSize(tuple);
        for (int i = 1; i <= size; i++) {
            ct.put("v" + i, encodeValue(getTupleElement(tuple, i), provider));
        }
        return ct;
    }

    // ---------- 容器解码 ----------
    private static Map<Object, Object> decodeMap(CompoundTag ct, HolderLookup.Provider provider) {
        Map<Object, Object> map = new HashMap<>();
        for (String rawKey : ct.getAllKeys()) {
            if ("$type".equals(rawKey)) continue;
            Object key = decodeKey(rawKey);
            if (key == null) continue;
            String valType = getValueTypeId(rawKey);
            String elemType = getElementTypeId(rawKey);
            map.put(key, decodeValue(ct.get(rawKey), valType, elemType, provider));
        }
        return map;
    }

    private static Collection<Object> decodeCollection(CompoundTag ct, String elementTypeId, HolderLookup.Provider provider) {
        ListTag list = ct.getList("list", Tag.TAG_COMPOUND);
        List<Object> tmp = new ArrayList<>();
        // 获取元素类型（从容器键名的 <> 中，但我们现在没有传递，所以先统一按字符串解码）
        for (Tag t : list) {
            tmp.add(decodeValue(t, elementTypeId, "", provider));
        }
        if ("set".equals(ct.getString("$type"))) {
            return new LinkedHashSet<>(tmp);
        }
        return tmp;
    }

    private static Object decodeTuple(CompoundTag ct, HolderLookup.Provider provider) {
        // 读取 v1..vN
        List<Object> elements = new ArrayList<>();
        for (int i = 1; ct.contains("v" + i); i++) {
            elements.add(decodeValue(ct.get("v" + i), "","", provider));
        }
        return createTuple(elements);
    }

    // ---------- 辅助 ----------
    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) { return (T) o; }

    private static boolean isTuple(Object obj) {
        return obj.getClass().getSimpleName().matches("T[2-7]");
    }

    private static int getTupleSize(Object tuple) {
        return Integer.parseInt(tuple.getClass().getSimpleName().substring(1));
    }

    private static Object getTupleElement(Object tuple, int index) {
        try {
            var field = tuple.getClass().getField("v" + index);
            return field.get(tuple);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object createTuple(List<Object> elements) {
        int size = elements.size();
        try {
            Class<?> tupleClass = Class.forName("com.thelema.thelemalib.data.T" + size);
            var ctor = tupleClass.getConstructors()[0];
            return ctor.newInstance(elements.toArray());
        } catch (Exception e) {
            LOGGER.error("Failed to create tuple T" + size, e);
            return null;
        }
    }

    // ---------- 基础类型枚举 ----------
    private enum BaseType {
        BYTE("byte", Byte.class, b -> Byte.toString((Byte) b), Byte::parseByte),
        SHORT("short", Short.class, s -> Short.toString((Short) s), Short::parseShort),
        INT("int", Integer.class, i -> Integer.toString((Integer) i), Integer::parseInt),
        LONG("long", Long.class, l -> Long.toString((Long) l), Long::parseLong),
        FLOAT("float", Float.class, f -> Float.toString((Float) f), Float::parseFloat),
        DOUBLE("double", Double.class, d -> Double.toString((Double) d), Double::parseDouble),
        BYTE_ARRAY("byte[]", byte[].class, arr -> Arrays.toString((byte[]) arr), s -> { throw new UnsupportedOperationException(); }),
        INT_ARRAY("int[]", int[].class, arr -> Arrays.toString((int[]) arr), s -> { throw new UnsupportedOperationException(); }),
        LONG_ARRAY("long[]", long[].class, arr -> Arrays.toString((long[]) arr), s -> { throw new UnsupportedOperationException(); }),
        STRING("string", String.class, s -> "\"" + s + "\"", s -> s.substring(1, s.length() - 1));

        final String typeId;
        final Class<?> clazz;
        final Function<Object, String> encoder;
        final Function<String, Object> decoder;

        BaseType(String typeId, Class<?> clazz, Function<Object, String> encoder, Function<String, Object> decoder) {
            this.typeId = typeId;
            this.clazz = clazz;
            this.encoder = encoder;
            this.decoder = decoder;
        }

        static BaseType byClass(Class<?> c) {
            for (BaseType t : values()) {
                if (t.clazz.isAssignableFrom(c)) return t;
            }
            return null;
        }

        static BaseType byId(String id) {
            for (BaseType t : values()) {
                if (t.typeId.equals(id)) return t;
            }
            return null;
        }

        static boolean isBaseType(String id) {
            return byId(id) != null;
        }

        String encodeToString(Object obj) {
            return encoder.apply(obj);
        }

        Object decodeFromString(String s) {
            return decoder.apply(s);
        }
    }
}