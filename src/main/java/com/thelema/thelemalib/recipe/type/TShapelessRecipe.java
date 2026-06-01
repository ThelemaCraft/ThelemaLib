package com.thelema.thelemalib.recipe.type;

import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.TRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public record TShapelessRecipe(String group, List<Ingredient> ingredients, ItemStack result,
                               RecipeHandle handle) implements CraftingRecipe {

    public TShapelessRecipe(String group, List<Ingredient> ingredients, ItemStack result, RecipeHandle handle) {
        this.group = group;
        this.ingredients = ingredients;
        this.result = result;
        this.handle = handle == null ? RecipeHandle.EMPTY : handle;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != ingredients.size()) return false;
        List<ItemStack> inputs = new ArrayList<>(input.items());
        for (Ingredient ing : ingredients) {
            boolean matched = false;
            for (int i = 0; i < inputs.size(); i++) {
                if (ing.test(inputs.get(i))) {
                    inputs.remove(i);
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack output = result.copy();
        if (!handle.copy().isEmpty()) {
            handle.apply(output, input);
        }
        return output;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
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
}