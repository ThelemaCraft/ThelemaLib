package com.thelema.thelemalib.recipe.serializer;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.type.TShapelessRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TShapelessSerializer implements RecipeSerializer<TShapelessRecipe> {
    public static final MapCodec<TShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Ingredient.CODEC.listOf().xmap(
                    list -> {
                        NonNullList<Ingredient> ingredients = NonNullList.create();
                        ingredients.addAll(list);
                        return ingredients;
                    },
                    List::copyOf
            ).fieldOf("ingredients").forGetter(TShapelessRecipe::ingredients),
            ItemStack.CODEC.fieldOf("template").forGetter(TShapelessRecipe::template),
            RecipeHandle.CODEC.optionalFieldOf("handle", RecipeHandle.EMPTY).forGetter(TShapelessRecipe::handle)
    ).apply(inst, TShapelessRecipe::new));

    @Override
    public @NotNull MapCodec<TShapelessRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, TShapelessRecipe> streamCodec() {
        return StreamCodec.of(this::toNetwork, this::fromNetwork);
    }

    private void toNetwork(RegistryFriendlyByteBuf buf, TShapelessRecipe recipe) {
        buf.writeVarInt(recipe.ingredients().size());
        for (Ingredient ing : recipe.ingredients()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ing);
        }
        ItemStack.STREAM_CODEC.encode(buf, recipe.template());
        RecipeHandle.STREAM_CODEC.encode(buf, recipe.handle());
    }

    private TShapelessRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
        for (int i = 0; i < size; i++) {
            ingredients.set(i, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
        }
        ItemStack template = ItemStack.STREAM_CODEC.decode(buf);
        RecipeHandle handle = RecipeHandle.STREAM_CODEC.decode(buf);
        return new TShapelessRecipe(ingredients, template, handle);
    }
}