package com.thelema.thelemalib.data.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 键 Codec 注册中心 —— 仅接受能编码为 StringTag 的 Codec。
 * <p>
 * 所有注册的 Codec 必须通过 {@code codec.encodeStart(NbtOps, value)} 产出 {@link net.minecraft.nbt.StringTag}，
 * 否则在键编码时会抛出异常。
 */
public final class KeyCodecRegistry {
    private static final Map<Class<?>, Entry<?>> BY_CLASS = new ConcurrentHashMap<>();
    private static final Map<String, Entry<?>> BY_ID = new ConcurrentHashMap<>();

    public static <T> void register(Class<T> clazz, String typeId, Codec<T> codec) {
        Entry<T> entry = new Entry<>(typeId, codec);
        BY_CLASS.put(clazz, entry);
        BY_ID.put(typeId, entry);
    }

    public static Entry<?> getByClass(Class<?> clazz) {
        Entry<?> e = BY_CLASS.get(clazz);
        if (e != null) return e;
        for (var en : BY_CLASS.entrySet()) {
            if (en.getKey().isAssignableFrom(clazz)) return en.getValue();
        }
        return null;
    }

    public static Entry<?> getById(String typeId) {
        return BY_ID.get(typeId);
    }

    public record Entry<T>(String typeId, Codec<T> codec) {}

    public static void init(){}

    static {

        // 布尔
        register(Boolean.class, "bool",
                Codec.STRING.xmap("true"::equalsIgnoreCase, b -> b ? "true" : "false"));

        // UUID
        register(UUID.class, "uuid",
                Codec.STRING.xmap(UUID::fromString, UUID::toString));

        // BlockPos（字符串 Codec）
        register(BlockPos.class, "Bpos",
                Codec.STRING.xmap(
                        s -> {
                            String[] p = s.split(",", 3);
                            return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                        },
                        p -> p.getX() + "," + p.getY() + "," + p.getZ()
                ));

        // Vec3（字符串 Codec）
        register(Vec3.class, "vec3",
                Codec.STRING.xmap(
                        s -> {
                            String[] p = s.split(",", 3);
                            return new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                        },
                        v -> v.x + "," + v.y + "," + v.z
                ));
    }
}