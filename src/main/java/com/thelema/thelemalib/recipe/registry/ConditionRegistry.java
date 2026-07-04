package com.thelema.thelemalib.recipe.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelema.thelemalib.recipe.tool.MathModifyTool;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class ConditionRegistry {
    private static final Map<String, ConditionPredicate> REGISTRY = new HashMap<>();

    static {
        register("item_id", (stack, json, provider) -> {
            String item = json.get("item").getAsString();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            ResourceLocation id = ResourceLocation.tryParse(item);
            if (id == null) return reverse;
            boolean matches = stack.is(BuiltInRegistries.ITEM.get(id));
            return reverse != matches;
        });

        register("damage", (stack, json, provider) -> {
            String op = json.get("op").getAsString();
            int value = json.get("value").getAsInt();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            int damage = stack.getOrDefault(DataComponents.DAMAGE, 0);
            boolean result = MathModifyTool.compare(op, damage, value);
            return reverse != result;
        });

        register("max_damage", (stack, json, provider) -> {
            String op = json.get("op").getAsString();
            int value = json.get("value").getAsInt();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            int max = stack.getOrDefault(DataComponents.MAX_DAMAGE, stack.getMaxDamage());
            boolean result = MathModifyTool.compare(op, max, value);
            return reverse != result;
        });

        register("custom_data", (stack, json, provider) -> {
            String key = json.get("key").getAsString();
            String op = json.get("op").getAsString();
            JsonElement valueElem = json.get("value");
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return reverse;
            CompoundTag tag = data.copyTag();
            String[] keys = key.split("\\.");
            CompoundTag current = tag;
            for (int i = 0; i < keys.length - 1; i++) {
                if (!current.contains(keys[i], CompoundTag.TAG_COMPOUND)) return reverse;
                current = current.getCompound(keys[i]);
            }
            String lastKey = keys[keys.length - 1];
            if (!current.contains(lastKey)) return reverse;
            Tag nbtTag = current.get(lastKey);
            boolean result = compareNbt(nbtTag, op, valueElem);
            return reverse != result;
        });

        register("tag", (stack, json, provider) -> {
            String tag = json.get("tag").getAsString();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            ResourceLocation location = ResourceLocation.tryParse(tag);
            if (location == null) return reverse;
            TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), location);
            boolean matches = stack.is(tagKey);
            return reverse != matches;
        });

        register("random", (stack, json, provider) -> {
            double chance = json.get("chance").getAsDouble();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = new Random().nextDouble() < chance;
            return reverse != result;
        });

        register("block_entity_data", (stack, json, provider) -> {
            String key = json.get("key").getAsString();
            String op = json.get("op").getAsString();
            JsonElement valueElem = json.get("value");
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();

            CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (data == null) return reverse;

            CompoundTag tag = data.copyTag();
            String[] keys = key.split("\\.");
            CompoundTag current = tag;
            for (int i = 0; i < keys.length - 1; i++) {
                if (!current.contains(keys[i], CompoundTag.TAG_COMPOUND)) return reverse;
                current = current.getCompound(keys[i]);
            }
            String lastKey = keys[keys.length - 1];
            if (!current.contains(lastKey)) return reverse;

            Tag nbtTag = current.get(lastKey);
            boolean result = compareNbt(nbtTag, op, valueElem);
            return reverse != result;
        });

        register("has_enchant", (stack, json, provider) -> {
            String enchantmentId = json.get("enchantment").getAsString();
            String op = json.has("op") ? json.get("op").getAsString() : ">=";
            int value = json.has("value") ? json.get("value").getAsInt() : 1;
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();

            ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
            if (id == null) return reverse;

            // 通过 provider 获取附魔注册表
            Optional<Holder.Reference<Enchantment>> holderOpt = provider.lookup(Registries.ENCHANTMENT)
                    .flatMap(lookup -> lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id)));
            if (holderOpt.isEmpty()) return reverse;
            Holder<Enchantment> holder = holderOpt.get();

            ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            int level = enchantments.getLevel(holder);

            boolean result = MathModifyTool.compare(op, level, value);
            return reverse != result;
        });

        register("food_nutrition", (stack, json, provider) -> {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) return false;
            String op = json.has("op") ? json.get("op").getAsString() : ">=";
            int value = json.get("value").getAsInt();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = MathModifyTool.compare(op, food.nutrition(), value);
            return reverse != result;
        });

        register("food_saturation", (stack, json, provider) -> {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) return false;
            String op = json.has("op") ? json.get("op").getAsString() : ">=";
            float value = json.get("value").getAsFloat();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = MathModifyTool.compare(op, food.saturation(), value);
            return reverse != result;
        });

        register("tool_speed", (stack, json, provider) -> {
            Tool tool = stack.get(DataComponents.TOOL);
            if (tool == null) return false;
            String op = json.has("op") ? json.get("op").getAsString() : ">=";
            float value = json.get("value").getAsFloat();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = MathModifyTool.compare(op, tool.defaultMiningSpeed(), value);
            return reverse != result;
        });

        register("damage_per_block", (stack, json, provider) -> {
            Tool tool = stack.get(DataComponents.TOOL);
            if (tool == null) return false;
            String op = json.has("op") ? json.get("op").getAsString() : ">=";
            float value = json.get("value").getAsFloat();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = MathModifyTool.compare(op, tool.damagePerBlock(), value);
            return reverse != result;
        });

        register("dyed_color", (stack, json, provider) -> {
            DyedItemColor color = stack.get(DataComponents.DYED_COLOR);
            if (color == null) return false;
            String op = json.has("op") ? json.get("op").getAsString() : "=";
            int value = json.get("value").getAsInt();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = MathModifyTool.compare(op, color.rgb(), value);
            return reverse != result;
        });

        register("potion_color", (stack, json, provider) -> {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) return false;
            int color = contents.customColor().orElse(0);
            String op = json.has("op") ? json.get("op").getAsString() : "=";
            int value = json.get("value").getAsInt();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = MathModifyTool.compare(op, color, value);
            return reverse != result;
        });

        register("banner_base_color", (stack, json, provider) -> {
            DyeColor actual = stack.get(DataComponents.BASE_COLOR);
            if (actual == null) return false;
            JsonElement valElem = json.get("value");
            DyeColor target = null;
            if (valElem.isJsonPrimitive()) {
                if (valElem.getAsJsonPrimitive().isNumber()) {
                    target = DyeColor.byId(valElem.getAsInt());
                } else if (valElem.getAsJsonPrimitive().isString()) {
                    target = DyeColor.byName(valElem.getAsString(), null);
                }
            }
            if (target == null) return false;
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = actual == target;
            return reverse != result;
        });

        register("max_stack_size", (stack, json, provider) -> {
            int max = stack.getOrDefault(DataComponents.MAX_STACK_SIZE, stack.getMaxStackSize());
            String op = json.has("op") ? json.get("op").getAsString() : ">=";
            int value = json.get("value").getAsInt();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = MathModifyTool.compare(op, max, value);
            return reverse != result;
        });

        register("repair_cost", (stack, json, provider) -> {
            int cost = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
            String op = json.has("op") ? json.get("op").getAsString() : ">=";
            int value = json.get("value").getAsInt();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = MathModifyTool.compare(op, cost, value);
            return reverse != result;
        });
    }

    private static boolean compareNbt(Tag tag, String op, JsonElement valueElem) {
        if (valueElem.isJsonPrimitive()) {
            var prim = valueElem.getAsJsonPrimitive();
            if (prim.isString()) {
                if (tag instanceof StringTag st) {
                    return switch (op) {
                        case "=", "==" -> st.getAsString().equals(prim.getAsString());
                        case "!=" -> !st.getAsString().equals(prim.getAsString());
                        default -> false;
                    };
                }
            } else if (prim.isNumber()) {
                double val = prim.getAsDouble();
                if (tag instanceof NumericTag nt) {
                    double num = nt.getAsDouble();
                    return switch (op) {
                        case "=", "==" -> num == val;
                        case "!=" -> num != val;
                        case ">" -> num > val;
                        case ">=" -> num >= val;
                        case "<" -> num < val;
                        case "<=" -> num <= val;
                        default -> false;
                    };
                }
            } else if (prim.isBoolean()) {
                boolean val = prim.getAsBoolean();
                if (tag instanceof ByteTag bt && (bt.getAsByte() == 0 || bt.getAsByte() == 1)) {
                    boolean b = bt.getAsByte() != 0;
                    return switch (op) {
                        case "=", "==" -> b == val;
                        case "!=" -> b != val;
                        default -> false;
                    };
                }
            }
        }
        return false;
    }

    public static void register(String type, ConditionPredicate conditionPredicate) {
        REGISTRY.put(type, conditionPredicate);
    }

    public static ConditionPredicate getPredicate(String type) {
        return REGISTRY.get(type);
    }

    public static void init() {}

    @FunctionalInterface
    public interface ConditionPredicate {
        boolean test(ItemStack stack, JsonObject json, HolderLookup.Provider provider);
    }
}