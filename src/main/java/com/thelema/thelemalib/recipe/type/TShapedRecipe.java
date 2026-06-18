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

import java.util.ArrayList;
import java.util.List;

public record TShapedRecipe(
        ShapedRecipePattern pattern,
        ItemStack template,
        JsonArray handle) implements CraftingRecipe {

    public TShapedRecipe(ShapedRecipePattern pattern, ItemStack template, JsonArray handle) {
        this.pattern = pattern;
        this.template = template;
        this.handle = handle;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return pattern.matches(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = template.copy();
        List<ItemStack> nonEmptyInputs = new ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) nonEmptyInputs.add(stack);
        }

        Context ctx = new Context(nonEmptyInputs, new ArrayList<>(List.of(result)));

        OutputHandler.handle(ctx, handle);
        return ctx.output.get(0);
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
    public RecipeType<?> getType() { return RecipeType.CRAFTING; }

    @Override
    public CraftingBookCategory category() { return CraftingBookCategory.MISC; }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return pattern.ingredients();
    }
}