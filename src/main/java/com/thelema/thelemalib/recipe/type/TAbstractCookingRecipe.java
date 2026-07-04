package com.thelema.thelemalib.recipe.type;

import com.google.gson.JsonArray;
import com.thelema.thelemalib.recipe.tool.Context;
import com.thelema.thelemalib.recipe.tool.OutputHandler;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class TAbstractCookingRecipe extends AbstractCookingRecipe {
    protected final JsonArray handle;

    public TAbstractCookingRecipe(RecipeType<?> type, String group, CookingBookCategory category,
                                  Ingredient ingredient, ItemStack template, float experience, int cookingTime,
                                  JsonArray handle) {
        super(type, group, category, ingredient, template, experience, cookingTime);
        this.handle = handle == null ? new JsonArray() : handle;
    }

    @Override
    public @NotNull ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider provider) {
        ItemStack result = super.assemble(input, provider);
        List<ItemStack> inputs = new ArrayList<>();
        if (!input.item().isEmpty()) inputs.add(input.item());

        Context ctx = new Context(inputs, new ArrayList<>(List.of(result)));
        OutputHandler.handle(ctx, handle, provider);

        return ctx.output.get(0);
    }

    public JsonArray getHandle() { return handle; }

    @Override
    public abstract @NotNull RecipeSerializer<?> getSerializer();
}