package com.thelema.thelemalib.recipe.type;

import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.TRecipeSerializers;
import it.unimi.dsi.fastutil.ints.IntList;
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
        RecipeHandle handle) implements CraftingRecipe {

    public TShapelessRecipe(NonNullList<Ingredient> ingredients, ItemStack template, RecipeHandle handle) {
        this.ingredients = ingredients;
        this.template = template.copy();
        this.handle = handle == null ? RecipeHandle.EMPTY : handle;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != ingredients.size()) {
            return false;
        }
        List<ItemStack> nonEmptyItems = new ArrayList<>(input.ingredientCount());
        for (ItemStack item : input.items()) {
            if (!item.isEmpty()) {
                nonEmptyItems.add(item);
            }
        }
        return RecipeMatcher.findMatches(nonEmptyItems, ingredients) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack output = template.copy();
        return handle.apply(output, input);
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
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

}