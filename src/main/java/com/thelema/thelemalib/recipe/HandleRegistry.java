package com.thelema.thelemalib.recipe;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.*;

public class HandleRegistry {
    // 存储组件类型 -> 字段类型映射（用于手动解析）
    public static final Map<DataComponentType<?>, Map<String, FieldType>> MANUAL_MAPPINGS = new IdentityHashMap<>();

    // 自动检测的注册
    public static void register(DeferredRegister.DataComponents registry) {
        // 这里无法在静态时获取所有条目，通常在注册后遍历
        // 可提供一个方法在 registry 构建完成后调用
        // 实际使用时，DeferredRegister 的 entries 可在所有注册完成后收集
        for (DeferredHolder<DataComponentType<?>, ? extends DataComponentType<?>> holder : registry.getEntries()) {
            DataComponentType<?> type = holder.get();
            // 对于简单类型，根据 codec 特征判断
            Map<String, FieldType> fields = analyzeComponent(type);
            if (fields != null && !fields.isEmpty()) {
                MANUAL_MAPPINGS.put(type, fields);
            }
        }
    }

    // 手动注册单个组件的字段类型
    public static void register(DeferredHolder<DataComponentType<?>, DataComponentType<?>> holder,
                                Map<String, FieldType> fieldTypes) {
        MANUAL_MAPPINGS.put(holder.get(), fieldTypes);
    }

    // 简单类型检测（支持 Boolean, Integer, Double, String 的单字段组件）
    public static Map<String, FieldType> analyzeComponent(DataComponentType<?> type) {
        Codec<?> codec = type.codec();
        // 如果 codec 是布尔
        if (codec == Codec.BOOL) return Map.of("value", FieldType.BOOLEAN);
        if (codec == Codec.INT) return Map.of("value", FieldType.INTEGER);
        if (codec == Codec.DOUBLE) return Map.of("value", FieldType.DOUBLE);
        if (codec == Codec.STRING) return Map.of("value", FieldType.STRING);
        return null; // 复杂类型需要手动指定
    }

    // 字段类型枚举
    public enum FieldType {
        BOOLEAN, INTEGER, DOUBLE, STRING
    }
}