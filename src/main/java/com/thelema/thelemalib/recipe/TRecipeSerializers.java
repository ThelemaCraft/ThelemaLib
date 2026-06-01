package com.thelema.thelemalib.recipe;


import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.recipe.serializer.TShapedSerializer;
import com.thelema.thelemalib.recipe.serializer.TShapelessSerializer;
import com.thelema.thelemalib.recipe.type.TShapedRecipe;
import com.thelema.thelemalib.recipe.type.TShapelessRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class TRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ThelemaLib.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TShapelessRecipe>> T_SHAPELESS_SERIALIZER =
            SERIALIZERS.register("t_shapeless", TShapelessSerializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TShapedRecipe>> T_SHAPED_SERIALIZER =
            SERIALIZERS.register("t_shaped", TShapedSerializer::new);
}
