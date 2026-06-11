package com.thelema.thelemalib.recipe.type;

import com.thelema.thelemalib.recipe.RecipeHandle;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.jetbrains.annotations.NotNull;

public abstract class TAbstractCookingRecipe extends AbstractCookingRecipe {
    private final RecipeHandle handle;

    public TAbstractCookingRecipe(RecipeType<?> type, String group, CookingBookCategory category,
                                  Ingredient ingredient, ItemStack template, float experience, int cookingTime,
                                  RecipeHandle handle) {
        super(type, group, category, ingredient, template, experience, cookingTime);
        this.handle = handle == null ? RecipeHandle.EMPTY : handle;
    }

    @Override
    public @NotNull ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        ItemStack baseResult = super.assemble(input, registries);
        return handle.apply(baseResult, input);
    }

    public RecipeHandle getHandle() {
        return handle;
    }

    @Override
    public abstract @NotNull RecipeSerializer<?> getSerializer();
}