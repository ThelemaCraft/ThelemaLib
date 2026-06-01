package com.thelema.thelemalib.recipe.serializer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.type.TShapelessRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.ArrayList;
import java.util.List;

public class TShapelessSerializer implements RecipeSerializer<TShapelessRecipe> {
    public static final MapCodec<TShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.STRING.fieldOf("group").forGetter(TShapelessRecipe::getGroup),
            Ingredient.CODEC.listOf().fieldOf("ingredients").forGetter(TShapelessRecipe::getIngredients),
            ItemStack.CODEC.fieldOf("result").forGetter(TShapelessRecipe::result),
            RecipeHandle.CODEC.optionalFieldOf("handle", RecipeHandle.EMPTY).forGetter(TShapelessRecipe::handle)
    ).apply(inst, TShapelessRecipe::new));

    @Override public MapCodec<TShapelessRecipe> codec() { return CODEC; }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, TShapelessRecipe> streamCodec() {
        return StreamCodec.of(this::toNetwork, this::fromNetwork);
    }

    private void toNetwork(RegistryFriendlyByteBuf buf, TShapelessRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeInt(recipe.getIngredients().size());
        for (Ingredient ing : recipe.getIngredients()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ing);
        }
        ItemStack.STREAM_CODEC.encode(buf, recipe.result());
        CompoundTag tag = (CompoundTag) RecipeHandle.CODEC.encodeStart(NbtOps.INSTANCE, recipe.handle()).getOrThrow();
        ByteBufCodecs.COMPOUND_TAG.encode(buf, tag);
    }

    private TShapelessRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
        String group = buf.readUtf();
        int size = buf.readInt();
        List<Ingredient> ingredients = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ingredients.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
        }
        ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
        CompoundTag tag = ByteBufCodecs.COMPOUND_TAG.decode(buf);
        RecipeHandle handle = RecipeHandle.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        return new TShapelessRecipe(group, ingredients, result, handle);
    }
}