package com.thelema.thelemalib.recipe.type;

import com.google.gson.JsonArray;
import com.thelema.thelemalib.recipe.TRecipeSerializers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.NotNull;

public class TCampfireCookingRecipe extends TAbstractCookingRecipe {
    public TCampfireCookingRecipe(String group, CookingBookCategory category,
                                  Ingredient ingredient, ItemStack template, float experience, int cookingTime,
                                  JsonArray handle) {
        super(RecipeType.CAMPFIRE_COOKING, group, category, ingredient, template, experience, cookingTime, handle);
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return TRecipeSerializers.T_CAMPFIRE_COOKING_SERIALIZER.get();
    }
}
