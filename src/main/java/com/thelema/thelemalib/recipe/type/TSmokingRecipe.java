package com.thelema.thelemalib.recipe.type;

import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.TRecipeSerializers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.NotNull;

public class TSmokingRecipe extends TAbstractCookingRecipe {
    public TSmokingRecipe(String group, CookingBookCategory category,
                          Ingredient ingredient, ItemStack template, float experience, int cookingTime,
                          RecipeHandle handle) {
        super(RecipeType.SMOKING, group, category, ingredient, template, experience, cookingTime, handle);
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return TRecipeSerializers.T_SMOKING_SERIALIZER.get();
    }
}
