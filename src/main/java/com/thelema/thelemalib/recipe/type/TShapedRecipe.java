package com.thelema.thelemalib.recipe.type;

import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.TRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public record TShapedRecipe(
        ShapedRecipePattern pattern,
        ItemStack template,
        RecipeHandle handle) implements CraftingRecipe {

    public TShapedRecipe(ShapedRecipePattern pattern, ItemStack template, RecipeHandle handle) {
        this.pattern = pattern;
        this.template = template;
        this.handle = handle == null ? RecipeHandle.EMPTY : handle;
    }

    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack output = template.copy();
        return handle.apply(output, input);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= pattern.width() && height >= pattern.height();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return template.copy();
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
        return CraftingBookCategory.MISC;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return pattern.ingredients();
    }

}