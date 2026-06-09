package com.thelema.thelemalib.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.ArrayList;
import java.util.List;

public record RecipeHandle(List<Copy> copy) {

    public static final RecipeHandle EMPTY = new RecipeHandle(List.of());

    public static final Codec<RecipeHandle> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    Copy.CODEC.listOf().fieldOf("copy").forGetter(RecipeHandle::copy)
            ).apply(inst, RecipeHandle::new)
    );

    public void apply(ItemStack output, RecipeWrapper input) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            items.add(input.getItem(i));
        }
        applyCommon(output, items);
    }

    public void apply(ItemStack output, CraftingInput input) {
        applyCommon(output, input.items());
    }

    public void apply(ItemStack output, SingleRecipeInput input) {
        applyCommon(output, List.of(input.item()));
    }

    public void apply(ItemStack output, SmithingRecipeInput input) {
        applyCommon(output, List.of(input.template(), input.base(), input.addition()));
    }

    private void applyCommon(ItemStack output, List<ItemStack> inputs) {
        for (Copy rule : copy) {
            ResourceLocation itemId = ResourceLocation.tryParse(rule.item());
            if (itemId == null) continue;
            Item item = BuiltInRegistries.ITEM.get(itemId);
            ItemStack source = inputs.stream()
                    .filter(s -> s.is(item))
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
            if (source.isEmpty()) continue;

            for (String entry : rule.data()) {
                // 解析组件ID和路径
                String[] parts = entry.split("/", 2);
                ResourceLocation componentId = ResourceLocation.tryParse(parts[0]);
                if (componentId == null) continue;
                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);
                if (type == null) continue;

                if (parts.length == 1) {
                    // 完整组件复制
                    copyComponent(source, output, type);
                } else {
                    // 路径复制：仅支持 minecraft:custom_data
                    if (componentId.equals(ResourceLocation.withDefaultNamespace("custom_data"))) {
                        copyCustomDataPath(source, output, parts[1]);
                    }
                    // 其他组件暂不支持路径复制，可忽略或抛出警告
                }
            }
        }
    }

    // 新增方法：复制 custom_data 中的指定路径
    private static void copyCustomDataPath(ItemStack src, ItemStack dst, String path) {
        CustomData srcData = src.get(DataComponents.CUSTOM_DATA);
        if (srcData == null) return;
        CompoundTag srcTag = srcData.copyTag();

        String[] keys = path.split("\\.");
        CompoundTag current = srcTag;
        for (int i = 0; i < keys.length - 1; i++) {
            if (current.contains(keys[i], Tag.TAG_COMPOUND)) {
                current = current.getCompound(keys[i]);
            } else {
                return; // 路径不存在
            }
        }
        String lastKey = keys[keys.length - 1];
        if (!current.contains(lastKey)) return;
        Tag value = current.get(lastKey);

        // 合并到目标物品的 custom_data
        CustomData dstData = dst.get(DataComponents.CUSTOM_DATA);
        CompoundTag dstTag = dstData != null ? dstData.copyTag() : new CompoundTag();
        CompoundTag target = dstTag;
        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            if (!target.contains(key, Tag.TAG_COMPOUND)) {
                target.put(key, new CompoundTag());
            }
            target = target.getCompound(key);
        }
        if (value != null) {
            target.put(lastKey, value);
        }else {
            throw new RuntimeException("copyCustomDataPath value == null");
        }
        dst.set(DataComponents.CUSTOM_DATA, CustomData.of(dstTag));
    }

    private static <T> void copyComponent(ItemStack src, ItemStack dst, DataComponentType<T> type) {
        T val = src.get(type);
        if (val != null) {
            dst.set(type, val);
        }
    }

    // 内部记录类：定义复制规则
    public record Copy(String item, List<String> data) {
        public static final Codec<Copy> CODEC = RecordCodecBuilder.create(inst ->
                inst.group(
                        Codec.STRING.fieldOf("item").forGetter(Copy::item),
                        Codec.STRING.listOf().fieldOf("data").forGetter(Copy::data)
                ).apply(inst, Copy::new)
        );
    }
}