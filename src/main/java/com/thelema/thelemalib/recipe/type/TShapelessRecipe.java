package com.thelema.thelemalib.recipe.type;

import com.google.gson.JsonArray;
import com.thelema.thelemalib.recipe.TRecipeSerializers;
import com.thelema.thelemalib.recipe.tool.Context;
import com.thelema.thelemalib.recipe.tool.OutputHandler;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.RecipeMatcher;

import java.util.ArrayList;
import java.util.List;

public record TShapelessRecipe(
        NonNullList<Ingredient> ingredients,
        ItemStack template,
        JsonArray handle) implements CraftingRecipe {

    public TShapelessRecipe(NonNullList<Ingredient> ingredients, ItemStack template, JsonArray handle) {
        this.ingredients = ingredients;
        this.template = template;
        this.handle = handle;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != ingredients.size()) {
            return false;
        }

        // 简单匹配：单个原料
        if (input.size() == 1 && ingredients.size() == 1) {
            return ingredients.get(0).test(input.getItem(0));
        }

        // 复杂匹配：使用 RecipeMatcher
        List<ItemStack> nonEmptyItems = new ArrayList<>();
        for (ItemStack item : input.items()) {
            if (!item.isEmpty()) {
                nonEmptyItems.add(item);
            }
        }

        return RecipeMatcher.findMatches(nonEmptyItems, ingredients) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        ItemStack result = template.copy();
        List<ItemStack> inputs = input.items().stream().filter(s -> !s.isEmpty()).toList();

        Context ctx = new Context(inputs, new ArrayList<>(List.of(result)));

        OutputHandler.handle(ctx, handle, provider);
        return ctx.output.get(0);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return template.copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TRecipeSerializers.T_SHAPELESS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() { return RecipeType.CRAFTING; }

    @Override
    public CraftingBookCategory category() { return CraftingBookCategory.MISC; }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }
}