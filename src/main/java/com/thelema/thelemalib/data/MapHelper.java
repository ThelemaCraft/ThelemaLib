package com.thelema.thelemalib.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;


/**
 * Map <-> NBT 转换，所有非字符串 Key 强制使用 "$类型|数据" 格式。
 * 类型标识符固定为 4 位。
 *
 * <p><strong>支持的 Key 类型：</strong>
 * <ul>
 *   <li>{@link String} – 原样保留</li>
 *   <li>{@link Byte} → "$byte|数值"</li>
 *   <li>{@link Short} → "$shrt|数值"</li>
 *   <li>{@link Integer} → "$int_|数值"</li>
 *   <li>{@link Long} → "$long|数值"</li>
 *   <li>{@link Float} → "$floa|数值"</li>
 *   <li>{@link Double} → "$doub|数值"</li>
 *   <li>{@link UUID} → "$uuid|字符串"</li>
 *   <li>{@link BlockPos} → "$Bpos|x,y,z"</li>
 *   <li>{@link Vec3} → "$vec3|x,y,z"</li>
 *   <li>其他已注册且 {@code allowedAsKey() == true} 的类型 → "$typeId|数据"</li>
 * </ul>
 *
 * <strong>支持的 Value 类型：</strong>
 * <ul>
 *   <li>基本 NBT 类型（Byte, Short, Integer, Long, Float, Double, String）</li>
 *   <li>{@link Boolean} → "$bool|0/1" 字符串</li>
 *   <li>{@link Map} → {$type:"map", ...}</li>
 *   <li>{@link List} → {$type:"list", list:[...]}</li>
 *   <li>{@link Set} → {$type:"set", list:[...]}</li>
 *   <li>{@link T2}, {@link T3} → {$type:"tuple2"/"tuple3", ...}</li>
 *   <li>已注册的小对象（UUID, BlockPos, Vec3, Boolean, 数字） → "$typeId|数据" 字符串</li>
 *   <li>已注册的大对象（ItemStack, IItemHandler, CompoundTag） → {$type:"$typeId", data:...} 原生 NBT</li>
 *   <li>其他未注册类型 → obj.toString()（丢失类型信息）</li>
 * </ul>
 * </p>
 */
public final class MapHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapHelper.class);

    private static final Map<Class<?>, TypeHandler<?>> HANDLERS = new ConcurrentHashMap<>();
    private static final Map<String, TypeHandler<?>> TYPE_ID_TO_HANDLER = new ConcurrentHashMap<>();

    public interface TypeHandler<T> {
        /** 4位标识符 */
        String typeId();
        /** 将对象编码为紧凑字符串（用于 Key 或小对象 Value） */
        String encodeToString(T value, HolderLookup.Provider provider);
        /** 从紧凑字符串解码 */
        T decodeFromString(String data, HolderLookup.Provider provider);
        /** 将对象编码为原生 NBT Tag（用于大对象 Value） */
        Tag encodeToTag(T value, HolderLookup.Provider provider);
        /** 从原生 NBT Tag 解码 */
        T decodeFromTag(Tag data, HolderLookup.Provider provider);
        /** 是否允许作为 Map 的 Key（默认 true） */
        default boolean allowedAsKey() { return true; }
    }

    public static <T> void register(Class<T> clazz, TypeHandler<T> handler) {
        HANDLERS.put(clazz, handler);
        TYPE_ID_TO_HANDLER.put(handler.typeId(), handler);
    }

    static {
        // 数字类型（Key 专用）
        register(Byte.class, new SmallTypeHandler<>(Byte.class, "byte", Object::toString, Byte::parseByte));
        register(Short.class, new SmallTypeHandler<>(Short.class, "shrt", Object::toString, Short::parseShort));
        register(Integer.class, new SmallTypeHandler<>(Integer.class, "int_", Object::toString, Integer::parseInt));
        register(Long.class, new SmallTypeHandler<>(Long.class, "long", Object::toString, Long::parseLong));
        register(Float.class, new SmallTypeHandler<>(Float.class, "floa", Object::toString, Float::parseFloat));
        register(Double.class, new SmallTypeHandler<>(Double.class, "doub", Object::toString, Double::parseDouble));

        // UUID
        register(UUID.class, new SmallTypeHandler<>(UUID.class, "uuid", UUID::toString, UUID::fromString));

        // BlockPos
        register(BlockPos.class, new SmallTypeHandler<>(BlockPos.class, "Bpos",
                pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                data -> {
                    String[] parts = data.split(",");
                    return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                }));

        // Vec3
        register(Vec3.class, new SmallTypeHandler<>(Vec3.class, "vec3",
                vec -> vec.x + "," + vec.y + "," + vec.z,
                data -> {
                    String[] parts = data.split(",");
                    return new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
                }));

        // Boolean
        register(Boolean.class, new SmallTypeHandler<>(Boolean.class, "bool", b -> b ? "1" : "0", "1"::equals));

        // ItemStack（大对象）
        register(ItemStack.class, new LargeTypeHandler<>(ItemStack.class, "$itemstack",
                (stack, prov) -> stack.save(prov, new CompoundTag()),
                (tag, prov) -> ItemStack.parse(prov, tag).orElse(ItemStack.EMPTY)));

        // IItemHandler（大对象）
        register(IItemHandler.class, new LargeTypeHandler<>(IItemHandler.class, "$iitemhandler",
                (handler, prov) -> {
                    CompoundTag nbt = new CompoundTag();
                    nbt.putInt("Size", handler.getSlots());
                    ListTag items = new ListTag();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            CompoundTag itemTag = new CompoundTag();
                            itemTag.putInt("Slot", i);
                            items.add(stack.save(prov, itemTag));
                        }
                    }
                    nbt.put("Items", items);
                    return nbt;
                },
                (tag, prov) -> {
                    CompoundTag nbt = (CompoundTag) tag;
                    int size = nbt.getInt("Size");
                    ItemStackHandler handler = new ItemStackHandler(size);
                    ListTag items = nbt.getList("Items", Tag.TAG_COMPOUND);
                    for (int i = 0; i < items.size(); i++) {
                        CompoundTag itemTag = items.getCompound(i);
                        int slot = itemTag.getInt("Slot");
                        ItemStack stack = ItemStack.parse(prov, itemTag).orElse(ItemStack.EMPTY);
                        handler.setStackInSlot(slot, stack);
                    }
                    return handler;
                }));

        // CompoundTag（大对象）
        register(CompoundTag.class, new LargeTypeHandler<>(CompoundTag.class, "$nbt",
                (ct, prov) -> ct,
                (tag, prov) -> (CompoundTag) tag));
    }

    // 辅助类：小对象处理器（仅实现字符串编解码）
    private record SmallTypeHandler<T>(Class<T> clazz, String typeId, Function<T, String> encoder,
                                           Function<String, T> decoder) implements TypeHandler<T> {
        @Override
        public String encodeToString(T value, HolderLookup.Provider provider) {
            return encoder.apply(value);
        }

        @Override
        public T decodeFromString(String data, HolderLookup.Provider provider) {
            return decoder.apply(data);
        }

        @Override
        public Tag encodeToTag(T value, HolderLookup.Provider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T decodeFromTag(Tag data, HolderLookup.Provider provider) {
            throw new UnsupportedOperationException();
        }
        }

    // 辅助类：大对象处理器（仅实现 Tag 编解码）
    private record LargeTypeHandler<T>(Class<T> clazz, String typeId, BiFunction<T, HolderLookup.Provider, Tag> encoder,
                                           BiFunction<Tag, HolderLookup.Provider, T> decoder) implements TypeHandler<T> {
        @Override
        public String encodeToString(T value, HolderLookup.Provider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T decodeFromString(String data, HolderLookup.Provider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Tag encodeToTag(T value, HolderLookup.Provider provider) {
            return encoder.apply(value, provider);
        }

        @Override
        public T decodeFromTag(Tag data, HolderLookup.Provider provider) {
            return decoder.apply(data, provider);
        }

        @Override
        public boolean allowedAsKey() {
            return false;
        } // 大对象默认不允许作为 Key
        }

    // ======================= 核心转换 =======================
    public static CompoundTag toNBT(Map<Object, Object> map, HolderLookup.Provider provider) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<Object, Object> e : map.entrySet()) {
            String key = encodeKey(e.getKey(), provider);
            if (key == null) {
                LOGGER.warn("Skipping entry with unsupported key type: {}", e.getKey().getClass());
                continue;
            }
            out.put(key, toTag(e.getValue(), provider));
        }
        return out;
    }

    public static Map<Object, Object> fromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        Map<Object, Object> map = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            Object mapKey = decodeKey(key, provider);
            if (mapKey == null) {
                LOGGER.warn("Skipping unknown key format: {}", key);
                continue;
            }
            map.put(mapKey, fromTag(tag.get(key), provider));
        }
        return map;
    }

    // ========== Key 编码/解码 ==========
    private static String encodeKey(Object key, HolderLookup.Provider provider) {
        if (key instanceof String s) return s;
        TypeHandler<?> handler = findHandler(key.getClass());
        if (handler != null && handler.allowedAsKey()) {
            return encodeKeyWithHandler(handler, key, provider);
        }
        return null;
    }

    private static Object decodeKey(String key, HolderLookup.Provider provider) {
        if (key == null) return null;
        if (key.startsWith("$")) {
            int pipe = key.indexOf('|');
            if (pipe == -1) return null;
            String typeId = key.substring(1, pipe);
            String data = key.substring(pipe + 1);
            TypeHandler<?> handler = TYPE_ID_TO_HANDLER.get(typeId);
            if (handler != null) {
                return handler.decodeFromString(data, provider);
            }
            return null;
        }
        return key;
    }

    // ========== Value 编码/解码 ==========
    private static Tag toTag(Object obj, HolderLookup.Provider provider) {
        if (obj == null) return new CompoundTag();

        // 基本 NBT 类型
        if (obj instanceof Byte b) return ByteTag.valueOf(b);
        if (obj instanceof Short s) return ShortTag.valueOf(s);
        if (obj instanceof Integer i) return IntTag.valueOf(i);
        if (obj instanceof Long l) return LongTag.valueOf(l);
        if (obj instanceof Float f) return FloatTag.valueOf(f);
        if (obj instanceof Double d) return DoubleTag.valueOf(d);
        if (obj instanceof String s) return StringTag.valueOf(s);

        // 容器递归
        if (obj instanceof Map<?,?> m) {
            CompoundTag container = new CompoundTag();
            container.putString("$type", "map");
            for (Map.Entry<?,?> e : m.entrySet()) {
                String k = encodeKey(e.getKey(), provider);
                if (k == null) continue;
                container.put(k, toTag(e.getValue(), provider));
            }
            return container;
        }
        if (obj instanceof List<?> list) {
            CompoundTag container = new CompoundTag();
            container.putString("$type", "list");
            ListTag listTag = new ListTag();
            for (Object e : list) listTag.add(toTag(e, provider));
            container.put("list", listTag);
            return container;
        }
        if (obj instanceof Set<?> set) {
            CompoundTag container = new CompoundTag();
            container.putString("$type", "set");
            ListTag listTag = new ListTag();
            for (Object e : set) listTag.add(toTag(e, provider));
            container.put("list", listTag);
            return container;
        }
        if (obj instanceof T2<?,?> t2) {
            CompoundTag container = new CompoundTag();
            container.putString("$type", "tuple2");
            container.put("left", toTag(t2.left, provider));
            container.put("right", toTag(t2.right, provider));
            return container;
        }
        if (obj instanceof T3<?,?,?> t3) {
            CompoundTag container = new CompoundTag();
            container.putString("$type", "tuple3");
            container.put("left", toTag(t3.left, provider));
            container.put("middle", toTag(t3.middle, provider));
            container.put("right", toTag(t3.right, provider));
            return container;
        }

        // 通过 TypeHandler 处理
        TypeHandler<?> handler = findHandler(obj.getClass());
        if (handler != null) {
            return encodeValueWithHandler(handler, obj, provider); // 调用辅助方法
        }

        // fallback
        return StringTag.valueOf(obj.toString());
    }

    private static Object fromTag(Tag tag, HolderLookup.Provider provider) {
        // 字符串形式的小对象
        if (tag instanceof StringTag str) {
            String s = str.getAsString();
            if (s.startsWith("$")) {
                int pipe = s.indexOf('|');
                if (pipe != -1) {
                    String typeId = s.substring(1, pipe);
                    String data = s.substring(pipe + 1);
                    TypeHandler<?> handler = TYPE_ID_TO_HANDLER.get(typeId);
                    if (handler != null) {
                        return handler.decodeFromString(data, provider);
                    }
                }
            }
            return s;
        }
        // CompoundTag 容器或大对象包装
        if (tag instanceof CompoundTag ct) {
            if (ct.contains("$type")) {
                String type = ct.getString("$type");
                switch (type) {
                    case "map" -> {
                        Map<Object, Object> map = new HashMap<>();
                        for (String k : ct.getAllKeys()) {
                            if (k.equals("$type")) continue;
                            Object key = decodeKey(k, provider);
                            if (key == null) continue;
                            map.put(key, fromTag(ct.get(k), provider));
                        }
                        return map;
                    }
                    case "list" -> {
                        ListTag listTag = ct.getList("list", Tag.TAG_COMPOUND);
                        List<Object> list = new ArrayList<>();
                        for (Tag t : listTag) list.add(fromTag(t, provider));
                        return list;
                    }
                    case "set" -> {
                        ListTag listTag = ct.getList("list", Tag.TAG_COMPOUND);
                        Set<Object> set = new LinkedHashSet<>();
                        for (Tag t : listTag) set.add(fromTag(t, provider));
                        return set;
                    }
                    case "tuple2" -> {
                        Object left = fromTag(ct.get("left"), provider);
                        Object right = fromTag(ct.get("right"), provider);
                        return new T2<>(left, right);
                    }
                    case "tuple3" -> {
                        Object left = fromTag(ct.get("left"), provider);
                        Object middle = fromTag(ct.get("middle"), provider);
                        Object right = fromTag(ct.get("right"), provider);
                        return new T3<>(left, middle, right);
                    }
                    default -> {
                        TypeHandler<?> handler = TYPE_ID_TO_HANDLER.get(type);
                        if (handler != null && ct.contains("data")) {
                            Tag data = ct.get("data");
                            return handler.decodeFromTag(data, provider);
                        }
                        return fromNbt(ct, provider);
                    }
                }
            } else {
                return fromNbt(ct, provider);
            }
        }
        // 基本数字类型
        if (tag instanceof ByteTag bt) return bt.getAsByte();
        if (tag instanceof ShortTag st) return st.getAsShort();
        if (tag instanceof IntTag it) return it.getAsInt();
        if (tag instanceof LongTag lt) return lt.getAsLong();
        if (tag instanceof FloatTag ft) return ft.getAsFloat();
        if (tag instanceof DoubleTag dt) return dt.getAsDouble();
        return tag.getAsString();
    }

    @SuppressWarnings("unchecked")
    private static <T> TypeHandler<T> findHandler(Class<T> clazz) {
        TypeHandler<?> handler = HANDLERS.get(clazz);
        if (handler != null) return (TypeHandler<T>) handler;
        for (Map.Entry<Class<?>, TypeHandler<?>> e : HANDLERS.entrySet()) {
            if (e.getKey().isAssignableFrom(clazz)) return (TypeHandler<T>) e.getValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> String encodeKeyWithHandler(TypeHandler<T> handler, Object key, HolderLookup.Provider provider) {
        return "$" + handler.typeId() + "|" + handler.encodeToString((T) key, provider);
    }

    @SuppressWarnings("unchecked")
    private static <T> Tag encodeValueWithHandler(TypeHandler<T> handler, Object value, HolderLookup.Provider provider) {
        try {
            Tag data = handler.encodeToTag((T) value, provider);
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("$type", handler.typeId());
            wrapper.put("data", data);
            return wrapper;
        } catch (UnsupportedOperationException e) {
            String str = handler.encodeToString((T) value, provider);
            return StringTag.valueOf("$" + handler.typeId() + "|" + str);
        }
    }

}