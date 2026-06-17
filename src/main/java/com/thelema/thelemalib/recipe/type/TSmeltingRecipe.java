// TSmeltingRecipe.java
package com.thelema.thelemalib.recipe.type;

import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.TRecipeSerializers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.jetbrains.annotations.NotNull;

public class TSmeltingRecipe extends TAbstractCookingRecipe {
    public TSmeltingRecipe(String group, CookingBookCategory category,
                           Ingredient ingredient, ItemStack template, float experience, int cookingTime,
                           RecipeHandle handle) {
        super(RecipeType.SMELTING, group, category, ingredient, template, experience, cookingTime, handle);
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return TRecipeSerializers.T_SMELTING_SERIALIZER.get();
    }

}
