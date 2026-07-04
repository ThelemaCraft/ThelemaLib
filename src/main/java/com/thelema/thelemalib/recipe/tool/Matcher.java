package com.thelema.thelemalib.recipe.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelema.thelemalib.recipe.registry.ConditionRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class Matcher {

    public static List<ItemStack> found(List<ItemStack> list, JsonElement matchElement, HolderLookup.Provider provider) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : list) {
            if (stack.isEmpty()) continue;
            boolean passes;
            if (matchElement.isJsonObject()) {
                passes = testEntry(stack, matchElement.getAsJsonObject(), provider);
            } else if (matchElement.isJsonArray()) {
                passes = testList(stack, matchElement.getAsJsonArray(), provider);
            } else {
                continue;
            }
            if (passes) result.add(stack);
        }
        return result;
    }

    public static boolean match(List<ItemStack> list, JsonElement matchElement, HolderLookup.Provider provider) {
        for (ItemStack stack : list) {
            if (stack.isEmpty()) continue;
            boolean passes;
            if (matchElement.isJsonObject()) {
                passes = testEntry(stack, matchElement.getAsJsonObject(), provider);
            } else if (matchElement.isJsonArray()) {
                passes = testList(stack, matchElement.getAsJsonArray(), provider);
            } else {
                continue;
            }
            if (passes) return true;
        }
        return false;
    }

    public static boolean testList(ItemStack stack, JsonArray array, HolderLookup.Provider provider) {
        for (JsonElement elem : array) {
            if (!elem.isJsonObject()) return false;
            if (!testEntry(stack, elem.getAsJsonObject(), provider)) return false;
        }
        return true;
    }

    public static boolean testEntry(ItemStack stack, JsonObject entry, HolderLookup.Provider provider) {
        ConditionRegistry.ConditionPredicate p = ConditionRegistry.getPredicate(entry.get("type").getAsString());
        return p != null && p.test(stack, entry, provider);
    }
}