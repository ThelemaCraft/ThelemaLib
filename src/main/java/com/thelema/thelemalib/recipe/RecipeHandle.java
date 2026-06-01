package com.thelema.thelemalib.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
            // 查找输入中匹配 rule.item 的第一个物品
            ResourceLocation itemId = ResourceLocation.tryParse(rule.item());
            if (itemId == null) continue;
            Item item = BuiltInRegistries.ITEM.get(itemId);
            ItemStack source = inputs.stream()
                    .filter(s -> s.is(item))
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
            if (source.isEmpty()) continue;

            // 复制组件
            for (String key : rule.data()) {
                ResourceLocation rl = ResourceLocation.tryParse(key);
                if (rl == null) continue;
                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
                if (type != null && source.has(type)) {
                    copyComponent(source, output, type);
                }
            }
        }
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