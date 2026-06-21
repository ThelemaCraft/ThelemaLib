package com.thelema.thelemalib.data.registry;

import com.mojang.serialization.Codec;
import com.thelema.thelemalib.data.tool.MapConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 值 Codec 注册中心 —— 仅管理自定义类型的 Codec。
 * <p>
 * 基础类型（数字、字符串、数组）及容器类型（Map、List、Set、Tuple）
 * 由 {@link MapConverter} 内聚处理，不在此注册。
 */
public final class ValueCodecRegistry {
    private static final Map<Class<?>, Entry<?>> BY_CLASS = new ConcurrentHashMap<>();
    private static final Map<String, Entry<?>> BY_ID = new ConcurrentHashMap<>();

    /**
     * 注册一个自定义值类型。
     *
     * @param clazz  类型 Class
     * @param typeId 唯一标识（不含特殊符号）
     * @param codec  对应的 Codec
     */
    public static <T> void register(Class<T> clazz, String typeId, Codec<T> codec) {
        Entry<T> entry = new Entry<>(typeId, codec);
        BY_CLASS.put(clazz, entry);
        BY_ID.put(typeId, entry);
    }

    static Entry<?> getByClass(Class<?> clazz) {
        Entry<?> e = BY_CLASS.get(clazz);
        if (e != null) return e;
        for (var en : BY_CLASS.entrySet()) {
            if (en.getKey().isAssignableFrom(clazz)) return en.getValue();
        }
        return null;
    }

    static Entry<?> getById(String typeId) {
        return BY_ID.get(typeId);
    }

    record Entry<T>(String typeId, Codec<T> codec) {}

    public static void init(){}

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
        register(IItemHandler.class, "iitemhandler",
                Codec.list(ItemStack.CODEC).xmap(
                        stacks -> {
                            ItemStackHandler h = new ItemStackHandler(stacks.size());
                            for (int i = 0; i < stacks.size(); i++) h.setStackInSlot(i, stacks.get(i));
                            return h;
                        },
                        h -> {
                            List<ItemStack> l = new ArrayList<>();
                            for (int i = 0; i < h.getSlots(); i++) l.add(h.getStackInSlot(i));
                            return l;
                        }
                ));

        // CompoundTag 自身（大对象）
        register(CompoundTag.class, "nbt", CompoundTag.CODEC);
    }
}