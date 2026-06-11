package com.thelema.thelemalib.recipe;

import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.recipe.serializer.*;
import com.thelema.thelemalib.recipe.type.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
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

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TSmeltingRecipe>> T_SMELTING_SERIALIZER =
            SERIALIZERS.register("t_smelting", () -> new TCookingSerializer<>(args ->
                    new TSmeltingRecipe(args.group(), args.category(), args.ingredient(),
                            args.template().orElse(ItemStack.EMPTY), args.experience(), args.cookingTime(), args.handle())));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TBlastingRecipe>> T_BLASTING_SERIALIZER =
            SERIALIZERS.register("t_blasting", () -> new TCookingSerializer<>(args ->
                    new TBlastingRecipe(args.group(), args.category(), args.ingredient(),
                            args.template().orElse(ItemStack.EMPTY), args.experience(), args.cookingTime(), args.handle())));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TCampfireCookingRecipe>> T_CAMPFIRE_COOKING_SERIALIZER =
            SERIALIZERS.register("t_campfire_cooking", () -> new TCookingSerializer<>(args ->
                    new TCampfireCookingRecipe(args.group(), args.category(), args.ingredient(),
                            args.template().orElse(ItemStack.EMPTY), args.experience(), args.cookingTime(), args.handle())));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TSmokingRecipe>> T_SMOKING_SERIALIZER =
            SERIALIZERS.register("t_smoking", () -> new TCookingSerializer<>(args ->
                    new TSmokingRecipe(args.group(), args.category(), args.ingredient(),
                            args.template().orElse(ItemStack.EMPTY), args.experience(), args.cookingTime(), args.handle())));
}