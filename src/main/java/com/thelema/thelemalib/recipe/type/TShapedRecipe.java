package com.thelema.thelemalib.recipe.type;

import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.TRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public record TShapedRecipe(String group, CraftingBookCategory category,
                            ShapedRecipe internal, ItemStack result,
                            RecipeHandle handle) implements CraftingRecipe {

    public TShapedRecipe(String group, CraftingBookCategory category,
                         ShapedRecipe internal, ItemStack result, RecipeHandle handle) {
        this.group = group;
        this.category = category;
        this.internal = internal;
        this.result = result;
        this.handle = handle == null ? RecipeHandle.EMPTY : handle;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return internal.matches(input, level);
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
    public boolean canCraftInDimensions(int width, int height) {
        return internal.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TRecipeSerializers.T_SHAPED_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    @Override
    public CraftingBookCategory category() {
        return category;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(internal.getIngredients());
        return list;
    }
}