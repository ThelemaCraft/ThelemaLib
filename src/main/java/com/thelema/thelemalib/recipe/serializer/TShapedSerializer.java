package com.thelema.thelemalib.recipe.serializer;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.type.TShapedRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.jetbrains.annotations.NotNull;

public class TShapedSerializer implements RecipeSerializer<TShapedRecipe> {
    public static final MapCodec<TShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            ShapedRecipePattern.MAP_CODEC.forGetter(TShapedRecipe::pattern),
            ItemStack.CODEC.fieldOf("template").forGetter(TShapedRecipe::template),
            RecipeHandle.CODEC.optionalFieldOf("handle", RecipeHandle.EMPTY).forGetter(TShapedRecipe::handle)
    ).apply(inst, TShapedRecipe::new));

    @Override
    public @NotNull MapCodec<TShapedRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, TShapedRecipe> streamCodec() {
        return StreamCodec.of(this::toNetwork, this::fromNetwork);
    }

    private void toNetwork(RegistryFriendlyByteBuf buf, TShapedRecipe recipe) {
        ShapedRecipePattern.STREAM_CODEC.encode(buf, recipe.pattern());
        ItemStack.STREAM_CODEC.encode(buf, recipe.template());
        RecipeHandle.STREAM_CODEC.encode(buf, recipe.handle());
    }

    private TShapedRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
        ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buf);
        ItemStack template = ItemStack.STREAM_CODEC.decode(buf);
        RecipeHandle handle = RecipeHandle.STREAM_CODEC.decode(buf);
        return new TShapedRecipe(pattern, template, handle);
    }
}