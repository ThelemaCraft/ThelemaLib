package com.thelema.thelemalib.data.registry;

import com.mojang.serialization.Codec;
import com.thelema.thelemalib.area.Area;
import com.thelema.thelemalib.area.AreaManager;
import com.thelema.thelemalib.area.AreaRegistry;
import com.thelema.thelemalib.data.tool.Adapt;
import com.thelema.thelemalib.data.tool.MapConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 值 Codec 注册中心 —— 仅管理自定义类型的 Codec。
 * <p>
 * 基础类型（数字、字符串、数组）及容器类型（Map、List、Set、Tuple）
 * 由 {@link MapConverter} 内聚处理，不在此注册。
 */
@SuppressWarnings("unchecked")
public final class ValueRegistry {
    private static final Map<Class<?>, CodecEntry<?>> CODEC_BY_CLASS = new ConcurrentHashMap<>();
    private static final Map<String, CodecEntry<?>> CODEC_BY_ID = new ConcurrentHashMap<>();
    private static final Map<Class<?>, AdaptEntry<?>> ADAPT_BY_CLASS = new ConcurrentHashMap<>();
    private static final Map<String, AdaptEntry<?>> ADAPT_BY_ID = new ConcurrentHashMap<>();

    /**
     * 注册一个自定义值类型。
     *
     * @param clazz  类型 Class
     * @param typeId 唯一标识（不含特殊符号）
     * @param codec  对应的 Codec
     */
    public static <T> void register(Class<T> clazz, String typeId, Codec<T> codec) {
        CodecEntry<T> codecEntry = new CodecEntry<>(typeId, codec);
        CODEC_BY_CLASS.put(clazz, codecEntry);
        CODEC_BY_ID.put(typeId, codecEntry);
    }

    public static CodecEntry<?> getCodecByClass(Class<?> clazz) {
        CodecEntry<?> e = CODEC_BY_CLASS.get(clazz);
        if (e != null) return e;
        for (var en : CODEC_BY_CLASS.entrySet()) {
            if (en.getKey().isAssignableFrom(clazz)) return en.getValue();
        }
        return null;
    }

    public static CodecEntry<?> getCodecById(String typeId) {
        return CODEC_BY_ID.get(typeId);
    }

    public record CodecEntry<T>(String typeId, Codec<T> codec) {
    }

    public static <T> void register(Class<T> clazz, String typeId, Adapt<T> adapt) {
        AdaptEntry<T> entry = new AdaptEntry<>(typeId, adapt);
        ADAPT_BY_CLASS.put(clazz, entry);
        ADAPT_BY_ID.put(typeId, entry);
    }

    public static AdaptEntry<?> getAdaptByClass(Class<?> clazz) {
        AdaptEntry<?> e = ADAPT_BY_CLASS.get(clazz);
        if (e != null) return e;
        for (var en : ADAPT_BY_CLASS.entrySet()) {
            if (en.getKey().isAssignableFrom(clazz)) return en.getValue();
        }
        return null;
    }

    public static AdaptEntry<?> getAdaptById(String typeId) {
        return ADAPT_BY_ID.get(typeId);
    }

    public record AdaptEntry<T>(String typeId, Adapt<T> adapt){

    }

    public static void init() {
    }

    // ========== 预注册常用自定义类型 ==========
    static {
        // 布尔值（字符串 Codec：true/false）
        register(Boolean.class, "bool",
                Codec.STRING.xmap("true"::equalsIgnoreCase, b -> b ? "true" : "false"));

        // UUID（字符串 Codec）
        register(UUID.class, "uuid",
                Codec.STRING.xmap(UUID::fromString, UUID::toString));

        // BlockPos（紧凑字符串 "x,y,z"）
        register(BlockPos.class, "Bpos",
                Codec.STRING.xmap(
                        s -> {
                            String[] p = s.split(",", 3);
                            return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                        },
                        p -> p.getX() + "," + p.getY() + "," + p.getZ()
                ));

        // Vec3（紧凑字符串 "x,y,z"）
        register(Vec3.class, "vec3",
                Codec.STRING.xmap(
                        s -> {
                            String[] p = s.split(",", 3);
                            return new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                        },
                        v -> v.x + "," + v.y + "," + v.z
                ));

        // ItemStack（使用原生 Codec，编码为 CompoundTag）
        register(ItemStack.class, "itemstack", ItemStack.CODEC);

        // IItemHandler（自定义 Codec，编码为 CompoundTag）
        register(IItemHandler.class, "iitemhandler", Codec.list(ItemStack.CODEC).xmap(
                stacks -> {
                    ItemStackHandler h = new ItemStackHandler(stacks.size());
                    for (int i = 0; i < stacks.size(); i++) h.setStackInSlot(i, stacks.get(i));
                    return h;
                }, h -> {
                    List<ItemStack> l = new ArrayList<>();
                    for (int i = 0; i < h.getSlots(); i++) l.add(h.getStackInSlot(i));
                    return l;
                }));

        // CompoundTag 自身（大对象）
        register(CompoundTag.class, "nbt", CompoundTag.CODEC);

        // Area
        register(Area.class, "area", new Adapt<>() {
            @Override
            public Tag toTag(Area area, HolderLookup.Provider provider) {
                CompoundTag tag = new CompoundTag();
                tag.putString("type", area.type);
                tag.putString("name", area.name);
                // 从 AABB 提取两个对角点（整数化）
                BlockPos min = new BlockPos(
                        (int) Math.floor(area.aabb.minX),
                        (int) Math.floor(area.aabb.minY),
                        (int) Math.floor(area.aabb.minZ)
                );
                BlockPos max = new BlockPos(
                        (int) Math.ceil(area.aabb.maxX) - 1,
                        (int) Math.ceil(area.aabb.maxY) - 1,
                        (int) Math.ceil(area.aabb.maxZ) - 1
                );
                tag.put("min", MapConverter.encodeValue(min, provider));
                tag.put("max", MapConverter.encodeValue(max, provider));
                return tag;
            }

            @Override
            public Area fromTag(Tag t, HolderLookup.Provider provider) {
                CompoundTag ct = (CompoundTag) t;
                String type = ct.getString("type");
                String name = ct.getString("name");
                BlockPos min = (BlockPos) MapConverter.decodeValue(ct.get("min"), "Bpos", provider);
                BlockPos max = (BlockPos) MapConverter.decodeValue(ct.get("max"), "Bpos", provider);
                return AreaRegistry.create(type, name, AreaManager.posToAABB(min, max));
            }
        });
    }
}