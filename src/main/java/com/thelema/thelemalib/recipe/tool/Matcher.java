package com.thelema.thelemalib.recipe.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelema.thelemalib.recipe.registry.ConditionRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public class Matcher {

    public static List<ItemStack> found(List<ItemStack> list, JsonElement matchElement) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : list) {
            if (stack.isEmpty()) continue;
            boolean passes;
            if (matchElement.isJsonObject()) {
                passes = testEntry(stack, matchElement.getAsJsonObject());
            } else if (matchElement.isJsonArray()) {
                passes = testList(stack, matchElement.getAsJsonArray());
            } else {
                continue;
            }
            if (passes) result.add(stack);
        }
        return result;
    }

    public static boolean match(List<ItemStack> list, JsonElement matchElement) {
        for (ItemStack stack : list) {
            if (stack.isEmpty()) continue;
            boolean passes;
            if (matchElement.isJsonObject()) {
                passes = testEntry(stack, matchElement.getAsJsonObject());
            } else if (matchElement.isJsonArray()) {
                passes = testList(stack, matchElement.getAsJsonArray());
            } else {
                continue;
            }
            if (passes) return true;
        }
        return false;
    }

    public static boolean testList(ItemStack stack, JsonArray array) {
        for (JsonElement elem : array) {
            if (!elem.isJsonObject()) return false;
            if (!testEntry(stack, elem.getAsJsonObject())) return false;
        }
        return true;
    }

    public static boolean testEntry(ItemStack stack, JsonObject entry) {
        BiPredicate<ItemStack, JsonObject> pred = ConditionRegistry.getPredicate(entry.get("type").getAsString());
        return pred != null && pred.test(stack, entry);
    }
}