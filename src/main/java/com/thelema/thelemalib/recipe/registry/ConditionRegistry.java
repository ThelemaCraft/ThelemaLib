package com.thelema.thelemalib.recipe.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelema.thelemalib.recipe.tool.MathModifyTool;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.*;
import java.util.function.BiPredicate;

public class ConditionRegistry {
    private static final Map<String, BiPredicate<ItemStack, JsonObject>> REGISTRY = new HashMap<>();

    static {
        register("item_id", (stack, json) -> {
            String item = json.get("item").getAsString();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            ResourceLocation id = ResourceLocation.tryParse(item);
            if (id == null) return reverse;
            boolean matches = stack.is(BuiltInRegistries.ITEM.get(id));
            return reverse != matches;
        });

        register("damage", (stack, json) -> {
            String op = json.get("op").getAsString();
            int value = json.get("value").getAsInt();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            int damage = stack.getOrDefault(DataComponents.DAMAGE, 0);
            boolean result = MathModifyTool.compare(op, damage, value);
            return reverse != result;
        });

        register("max_damage", (stack, json) -> {
            String op = json.get("op").getAsString();
            int value = json.get("value").getAsInt();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            int max = stack.getOrDefault(DataComponents.MAX_DAMAGE, stack.getMaxDamage());
            boolean result = MathModifyTool.compare(op, max, value);
            return reverse != result;
        });

        register("custom_data", (stack, json) -> {
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

        register("tag", (stack, json) -> {
            String tag = json.get("tag").getAsString();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            ResourceLocation location = ResourceLocation.tryParse(tag);
            if (location == null) return reverse;
            TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), location);
            boolean matches = stack.is(tagKey);
            return reverse != matches;
        });

        register("random", (stack, json) -> {
            double chance = json.get("chance").getAsDouble();
            boolean reverse = json.has("reverse") && json.get("reverse").getAsBoolean();
            boolean result = new Random().nextDouble() < chance;
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

    public static void register(String type, BiPredicate<ItemStack, JsonObject> predicate) {
        REGISTRY.put(type, predicate);
    }

    public static BiPredicate<ItemStack, JsonObject> getPredicate(String type) {
        return REGISTRY.get(type);
    }

    public static void init(){}
}