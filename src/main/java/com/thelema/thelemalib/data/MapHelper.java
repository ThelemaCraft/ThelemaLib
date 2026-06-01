package com.thelema.thelemalib.data;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Pattern;

public class MapHelper {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    // =======================   Gson 实例（带自定义适配器）   ===============================

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Number.class, new NumberTypeAdapter())
            .registerTypeAdapter(T2.class, new Tuple2Deserializer())
            .registerTypeAdapter(T3.class, new Tuple3Deserializer())
            .registerTypeAdapter(IItemHandler.class, new IItemHandlerTypeAdapter())
            .registerTypeAdapter(ItemStackHandler.class, new IItemHandlerTypeAdapter())
            .create();

    // ---------- 数字类型适配器 ----------
    private static class NumberTypeAdapter extends TypeAdapter<Number> {
        @Override
        public void write(JsonWriter out, Number value) throws IOException {
            out.value(value);
        }

        @Override
        public Number read(JsonReader in) throws IOException {
            String s = in.nextString();
            if (s.contains(".") || s.contains("e") || s.contains("E")) {
                return Double.parseDouble(s);
            } else {
                long longVal = Long.parseLong(s);
                if (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE) {
                    return (int) longVal;
                }
                return longVal;
            }
        }
    }

    // ---------- Tuple2 反序列化器 ----------
    private static class Tuple2Deserializer implements JsonDeserializer<T2<?, ?>> {
        @Override
        public T2<?, ?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonObject()) throw new JsonParseException("Expected object for Tuple2");
            JsonObject obj = json.getAsJsonObject();
            JsonElement tupleElement = obj.get("$tuple2");
            if (tupleElement == null || !tupleElement.isJsonArray()) throw new JsonParseException("Missing $tuple2 array");
            JsonArray array = tupleElement.getAsJsonArray();
            if (array.size() != 2) throw new JsonParseException("Tuple2 array must have 2 elements");
            Type[] elementTypes = ((ParameterizedType) typeOfT).getActualTypeArguments();
            Object left = context.deserialize(array.get(0), elementTypes[0]);
            Object right = context.deserialize(array.get(1), elementTypes[1]);
            return new T2<>(left, right);
        }
    }

    // ---------- Tuple3 反序列化器 ----------
    private static class Tuple3Deserializer implements JsonDeserializer<T3<?, ?, ?>> {
        @Override
        public T3<?, ?, ?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonObject()) throw new JsonParseException("Expected object for Tuple3");
            JsonObject obj = json.getAsJsonObject();
            JsonElement tupleElement = obj.get("$tuple3");
            if (tupleElement == null || !tupleElement.isJsonArray()) throw new JsonParseException("Missing $tuple3 array");
            JsonArray array = tupleElement.getAsJsonArray();
            if (array.size() != 3) throw new JsonParseException("Tuple3 array must have 3 elements");
            Type[] elementTypes = ((ParameterizedType) typeOfT).getActualTypeArguments();
            Object left = context.deserialize(array.get(0), elementTypes[0]);
            Object middle = context.deserialize(array.get(1), elementTypes[1]);
            Object right = context.deserialize(array.get(2), elementTypes[2]);
            return new T3<>(left, middle, right);
        }
    }

    // 自定义 IItemHandler 类型适配器
    private static class IItemHandlerTypeAdapter extends TypeAdapter<IItemHandler> {
        @Override
        public void write(JsonWriter out, IItemHandler handler) throws IOException {
            out.beginObject();
            out.name("type").value("$iitemhandler");
            out.name("size").value(handler.getSlots());
            out.name("items").beginArray();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    out.beginObject();
                    out.name("slot").value(i);
                    out.name("item").value(stack.getItem().getDescriptionId());
                    out.name("count").value(stack.getCount());
                    if (stack.has(DataComponents.CUSTOM_DATA)) {
                        out.name("tag").value(stack.get(DataComponents.CUSTOM_DATA).toString());
                    }
                    out.endObject();
                }
            }
            out.endArray();
            out.endObject();
        }

        @Override
        public IItemHandler read(JsonReader in) throws IOException {
            in.skipValue();
            return null;
        }
    }

    // =======================   NBT   ===============================

    public static CompoundTag toNBT(Map<Object, Object> map, HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            String key;
            Object k = entry.getKey();
            if (k == null) {
                key = "null";
            } else if (k instanceof BlockPos pos) {
                key = "B" + pos.asLong();   // 使用前缀 B + 编码的 long
            } else if (k instanceof UUID uuid) {
                key = uuid.toString();
            } else {
                key = k.toString();
            }
            tag.put(key, toTag(entry.getValue(), provider));
        }
        return tag;
    }

    public static Map<Object, Object> fromNbt(CompoundTag nbt, HolderLookup.Provider provider) {
        Map<Object, Object> map = new HashMap<>();
        for (String key : nbt.getAllKeys()) {
            Object mapKey = key;
            if (key.startsWith("B")) {
                try {
                    long encoded = Long.parseLong(key.substring(1));
                    mapKey = BlockPos.of(encoded);
                } catch (NumberFormatException ignored) {}
            } else if (isUuidString(key)) {
                try {
                    mapKey = UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {}
            }
            Tag tag = nbt.get(key);
            Object value = fromTag(tag, provider);
            map.put(mapKey, value);
        }
        return map;
    }

    private static Tag toTag(Object obj, HolderLookup.Provider provider) {
        if (obj == null) return new CompoundTag();

        // 新增 BlockPos 处理
        if (obj instanceof BlockPos pos) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("type", "$blockpos");
            wrapper.putInt("x", pos.getX());
            wrapper.putInt("y", pos.getY());
            wrapper.putInt("z", pos.getZ());
            return wrapper;
        }

        // 修改1：CompoundTag 包装为 $nbt
        if (obj instanceof CompoundTag ct) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("type", "$nbt");
            wrapper.put("data", ct);
            return wrapper;
        }

        // 修改2：Map 直接序列化为普通 CompoundTag（无标记）
        if (obj instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> m = (Map<Object, Object>) map;
            return toNBT(m, provider);
        }

        // ---------- 需要标记的类型 ----------
        // List
        if (obj instanceof List<?> list) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("type", "$list");
            ListTag content = new ListTag();
            for (Object e : list) {
                Tag elemTag = toTag(e, provider);
                CompoundTag elemWrapper = new CompoundTag();
                elemWrapper.put("value", elemTag);
                content.add(elemWrapper);
            }
            wrapper.put("list", content);
            return wrapper;
        }

        // Set
        if (obj instanceof Set<?> set) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("type", "$set");
            ListTag content = new ListTag();
            for (Object e : set) {
                Tag elemTag = toTag(e, provider);
                CompoundTag elemWrapper = new CompoundTag();
                elemWrapper.put("value", elemTag);
                content.add(elemWrapper);
            }
            wrapper.put("list", content);
            return wrapper;
        }

        // Tuple2
        if (obj instanceof T2<?, ?> tuple) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("type", "$tuple2");
            wrapper.put("left", toTag(tuple.left, provider));
            wrapper.put("right", toTag(tuple.right, provider));
            return wrapper;
        }

        // Tuple3
        if (obj instanceof T3<?, ?, ?> tuple) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("type", "$tuple3");
            wrapper.put("left", toTag(tuple.left, provider));
            wrapper.put("middle", toTag(tuple.middle, provider));
            wrapper.put("right", toTag(tuple.right, provider));
            return wrapper;
        }

        // Vec3
        if (obj instanceof Vec3 vec) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("type", "$vec3");
            wrapper.putDouble("x", vec.x);
            wrapper.putDouble("y", vec.y);
            wrapper.putDouble("z", vec.z);
            return wrapper;
        }

        // IItemHandler
        if (obj instanceof IItemHandler handler) {
            CompoundTag nbt = new CompoundTag();
            ListTag items = new ListTag();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putInt("Slot", i);
                    items.add(stack.save(provider, itemTag));
                }
            }
            nbt.put("Items", items);
            nbt.putInt("Size", handler.getSlots());

            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("type", "$iitemhandler");
            wrapper.put("data", nbt);
            return wrapper;
        }

        // ItemStack
        if (obj instanceof ItemStack stack) {
            CompoundTag stackNbt = (CompoundTag) stack.save(provider, new CompoundTag());
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("type", "$itemstack");
            wrapper.put("data", stackNbt);
            return wrapper;
        }

        // Boolean
        if (obj instanceof Boolean b) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString("type", "$boolean");
            wrapper.putBoolean("value", b);
            return wrapper;
        }

        // 基本数字类型
        if (obj instanceof Byte b) return ByteTag.valueOf(b);
        if (obj instanceof Short s) return ShortTag.valueOf(s);
        if (obj instanceof Integer i) return IntTag.valueOf(i);
        if (obj instanceof Long l) return LongTag.valueOf(l);
        if (obj instanceof Float f) return FloatTag.valueOf(f);
        if (obj instanceof Double d) return DoubleTag.valueOf(d);

        // 字符串、UUID
        if (obj instanceof String s) return StringTag.valueOf(s);
        if (obj instanceof UUID uuid) return StringTag.valueOf(uuid.toString());

        // fallback
        return StringTag.valueOf(obj.toString());
    }

    private static Object fromTag(Tag tag, HolderLookup.Provider provider) {
        // 处理 CompoundTag 包装类型
        if (tag instanceof CompoundTag ct && ct.contains("type")) {
            String type = ct.getString("type");
            switch (type) {
                case "$nbt" -> {
                    return ct.getCompound("data");
                }
                case "$list" -> {
                    ListTag listTag = ct.getList("list", Tag.TAG_COMPOUND);
                    List<Object> list = new ArrayList<>();
                    for (Tag t : listTag) {
                        if (t instanceof CompoundTag elem) {
                            list.add(fromTag(elem.get("value"), provider));
                        }
                    }
                    return list;
                }
                case "$set" -> {
                    ListTag listTag = ct.getList("list", Tag.TAG_COMPOUND);
                    Set<Object> set = new LinkedHashSet<>();
                    for (Tag t : listTag) {
                        if (t instanceof CompoundTag elem) {
                            set.add(fromTag(elem.get("value"), provider));
                        }
                    }
                    return set;
                }
                case "$tuple2" -> {
                    Object left = fromTag(ct.get("left"), provider);
                    Object right = fromTag(ct.get("right"), provider);
                    return new T2<>(left, right);
                }
                case "$tuple3" -> {
                    Object left = fromTag(ct.get("left"), provider);
                    Object middle = fromTag(ct.get("middle"), provider);
                    Object right = fromTag(ct.get("right"), provider);
                    return new T3<>(left, middle, right);
                }
                case "$vec3" -> {
                    double x = ct.getDouble("x");
                    double y = ct.getDouble("y");
                    double z = ct.getDouble("z");
                    return new Vec3(x, y, z);
                }
                case "$iitemhandler" -> {
                    CompoundTag data = ct.getCompound("data");
                    return deserializeItemHandler(data, provider);
                }
                case "$itemstack" -> {
                    CompoundTag data = ct.getCompound("data");
                    return ItemStack.parse(provider, data).orElse(ItemStack.EMPTY);
                }
                case "$boolean" -> {
                    return ct.getBoolean("value");
                }
                case "$blockpos" -> {
                    int x = ct.getInt("x");
                    int y = ct.getInt("y");
                    int z = ct.getInt("z");
                    return new BlockPos(x, y, z);
                }
                default -> {
                }
            }
        }

        // 普通 CompoundTag：保持为 CompoundTag（不展开）
        if (tag instanceof CompoundTag ct) {
            // 检查是否是旧的 Vec3 格式（兼容旧数据）
            if (ct.contains("Type") && ct.getString("Type").equals("Vec3")) {
                double x = ct.getDouble("x");
                double y = ct.getDouble("y");
                double z = ct.getDouble("z");
                return new Vec3(x, y, z);
            }
            // 检查是否是旧的 IItemHandler 格式
            if (ct.contains("Items", Tag.TAG_LIST) && ct.contains("Size", Tag.TAG_INT)) {
                return deserializeItemHandler(ct, provider);
            }

            // 其他普通 CompoundTag 保持原样
            return fromNbt(ct, provider);
        }

        // 基本数字类型
        if (tag instanceof ByteTag bt) return bt.getAsByte();
        if (tag instanceof ShortTag st) return st.getAsShort();
        if (tag instanceof IntTag it) return it.getAsInt();
        if (tag instanceof LongTag lt) return lt.getAsLong();
        if (tag instanceof FloatTag ft) return ft.getAsFloat();
        if (tag instanceof DoubleTag dt) return dt.getAsDouble();
        if (tag instanceof StringTag str) {
            String s = str.getAsString();
            if (isUuidString(s)) {
                try {
                    return UUID.fromString(s);
                } catch (IllegalArgumentException ignored) {}
            }
            return s;
        }
        return tag.getAsString();
    }

    private static IItemHandler deserializeItemHandler(CompoundTag nbt, HolderLookup.Provider provider) {
        int size = nbt.getInt("Size");
        ItemStackHandler handler = new ItemStackHandler(size);
        ListTag items = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemTag = items.getCompound(i);
            int slot = itemTag.getInt("Slot");
            ItemStack stack = ItemStack.parse(provider, itemTag).orElse(ItemStack.EMPTY);
            handler.setStackInSlot(slot, stack);
        }
        return handler;
    }

    // =======================   Json   ===============================

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static String toJson(Map<Object, Object> map, HolderLookup.Provider provider) {
        Object plain = toJsonValue(map, provider);
        return GSON.toJson(plain);
    }

    public static Map<Object, Object> fromJson(String json, HolderLookup.Provider provider) {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> plain = GSON.fromJson(json, type);
        if (plain == null) return new HashMap<>();
        Object result = fromJsonValue(plain, provider);
        result = normalizeNumbers(result);
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) result;
            return map;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> fromJson(String json, Type type, HolderLookup.Provider provider) {
        Map<K, V> map = GSON.fromJson(json, type);
        return (Map<K, V>) normalizeNumbers(map);
    }

    public static <V> Map<UUID, V> convertKeysToUuid(Map<String, V> stringKeyMap) {
        Map<UUID, V> result = new HashMap<>();
        for (Map.Entry<String, V> entry : stringKeyMap.entrySet()) {
            result.put(UUID.fromString(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Object normalizeNumbers(Object obj) {
        if (obj instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                long l = d.longValue();
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            }
            return d;
        }
        if (obj instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) obj;
            Map<Object, Object> newMap = new HashMap<>();
            for (Map.Entry<Object, Object> e : map.entrySet()) {
                newMap.put(e.getKey(), normalizeNumbers(e.getValue()));
            }
            return newMap;
        }
        if (obj instanceof List<?>) {
            List<Object> list = (List<Object>) obj;
            List<Object> newList = new ArrayList<>();
            for (Object e : list) {
                newList.add(normalizeNumbers(e));
            }
            return newList;
        }
        if (obj instanceof Set<?>) {
            Set<Object> set = (Set<Object>) obj;
            Set<Object> newSet = new LinkedHashSet<>();
            for (Object e : set) {
                newSet.add(normalizeNumbers(e));
            }
            return newSet;
        }
        if (obj instanceof T2<?, ?>) {
            T2<?, ?> t = (T2<?, ?>) obj;
            return new T2<>(normalizeNumbers(t.left), normalizeNumbers(t.right));
        }
        if (obj instanceof T3<?, ?, ?>) {
            T3<?, ?, ?> t = (T3<?, ?, ?>) obj;
            return new T3<>(normalizeNumbers(t.left), normalizeNumbers(t.middle), normalizeNumbers(t.right));
        }
        return obj;
    }

    private static Object toJsonValue(Object obj, HolderLookup.Provider provider) {
        if (obj == null) return null;
        if (obj instanceof BlockPos pos) {
            return Map.of("$blockpos", List.of(pos.getX(), pos.getY(), pos.getZ()));
        }
        if (obj instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> m = (Map<Object, Object>) obj;
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<Object, Object> e : m.entrySet()) {
                out.put(e.getKey().toString(), toJsonValue(e.getValue(), provider));
            }
            return out;
        }
        if (obj instanceof List<?>) {
            List<Object> list = (List<Object>) obj;
            List<Object> out = new ArrayList<>();
            for (Object e : list) out.add(toJsonValue(e, provider));
            return out;
        }
        if (obj instanceof Set<?>) {
            Set<?> set = (Set<?>) obj;
            List<Object> list = new ArrayList<>();
            for (Object e : set) list.add(toJsonValue(e, provider));
            return Map.of("$set", list);
        }
        if (obj instanceof T2<?, ?> t) {
            return Map.of("$tuple2", List.of(toJsonValue(t.left, provider), toJsonValue(t.right, provider)));
        }
        if (obj instanceof T3<?, ?, ?> t) {
            return Map.of("$tuple3", List.of(toJsonValue(t.left, provider), toJsonValue(t.middle, provider), toJsonValue(t.right, provider)));
        }
        if (obj instanceof IItemHandler handler) {
            CompoundTag nbt = (CompoundTag) toTag(handler, provider);
            Map<Object, Object> map = fromNbt(nbt, provider);
            return Map.of("$iitemhandler", toJsonValue(map, provider));
        }
        if (obj instanceof ItemStack stack) {
            CompoundTag nbt = (CompoundTag) toTag(stack, provider);
            Map<Object, Object> map = fromNbt(nbt, provider);
            return Map.of("$itemstack", toJsonValue(map, provider));
        }
        if (obj instanceof Vec3 v) {
            return Map.of("x", v.x, "y", v.y, "z", v.z);
        }
        if (obj instanceof UUID uuid) {
            return uuid.toString();
        }
        if (obj instanceof Number) return obj;
        if (obj instanceof Boolean) return obj;
        if (obj instanceof String) return obj;
        return obj.toString();
    }

    private static Object fromJsonValue(Object obj, HolderLookup.Provider provider) {
        if (obj == null) return null;
        if (obj instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            if (map.containsKey("$blockpos")) {
                Object val = map.get("$blockpos");
                if (val instanceof List<?> list && list.size() == 3) {
                    int x = ((Number) list.get(0)).intValue();
                    int y = ((Number) list.get(1)).intValue();
                    int z = ((Number) list.get(2)).intValue();
                    return new BlockPos(x, y, z);
                }
            }
            if (map.containsKey("$set")) {
                Object val = map.get("$set");
                if (val instanceof List<?> list) {
                    Set<Object> set = new LinkedHashSet<>();
                    for (Object e : list) set.add(fromJsonValue(e, provider));
                    return set;
                }
            }
            if (map.containsKey("$tuple2")) {
                Object val = map.get("$tuple2");
                if (val instanceof List<?> list && list.size() == 2) {
                    return new T2<>(fromJsonValue(list.get(0), provider), fromJsonValue(list.get(1), provider));
                }
            }
            if (map.containsKey("$tuple3")) {
                Object val = map.get("$tuple3");
                if (val instanceof List<?> list && list.size() == 3) {
                    return new T3<>(fromJsonValue(list.get(0), provider), fromJsonValue(list.get(1), provider), fromJsonValue(list.get(2), provider));
                }
            }
            if (map.containsKey("$iitemhandler")) {
                Object val = map.get("$iitemhandler");
                Map<Object, Object> data = (Map<Object, Object>) fromJsonValue(val, provider);
                CompoundTag nbt = toNBT(data, provider);
                return deserializeItemHandler(nbt, provider);
            }
            if (map.containsKey("$itemstack")) {
                Object val = map.get("$itemstack");
                Map<Object, Object> data = (Map<Object, Object>) fromJsonValue(val, provider);
                CompoundTag nbt = toNBT(data, provider);
                return ItemStack.parse(provider, nbt).orElse(ItemStack.EMPTY);
            }
            Map<Object, Object> out = new HashMap<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                Object key = e.getKey();
                if (key instanceof String s && isUuidString(s)) {
                    try {
                        key = UUID.fromString(s);
                    } catch (IllegalArgumentException ignored) {}
                }
                out.put(key, fromJsonValue(e.getValue(), provider));
            }
            return out;
        }
        if (obj instanceof List<?>) {
            List<Object> list = (List<Object>) obj;
            List<Object> out = new ArrayList<>();
            for (Object e : list) out.add(fromJsonValue(e, provider));
            return out;
        }
        if (obj instanceof String s) {
            if (isUuidString(s)) {
                try {
                    return UUID.fromString(s);
                } catch (IllegalArgumentException ignored) {}
            }
            return s;
        }
        return obj;
    }

    private static boolean isUuidString(String s) {
        return s != null && UUID_PATTERN.matcher(s).matches();
    }
}