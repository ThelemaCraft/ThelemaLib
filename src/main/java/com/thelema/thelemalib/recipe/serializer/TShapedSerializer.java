package com.thelema.thelemalib.recipe.serializer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.type.TShapedRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

public class TShapedSerializer implements RecipeSerializer<TShapedRecipe> {
    public static final MapCodec<TShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.STRING.fieldOf("group").forGetter(TShapedRecipe::getGroup),
            CraftingBookCategory.CODEC.fieldOf("category").forGetter(TShapedRecipe::category),
            ShapedRecipe.Serializer.CODEC.fieldOf("internal").forGetter(TShapedRecipe::internal),
            ItemStack.CODEC.fieldOf("result").forGetter(TShapedRecipe::result),
            RecipeHandle.CODEC.optionalFieldOf("handle", RecipeHandle.EMPTY).forGetter(TShapedRecipe::handle)
    ).apply(inst, TShapedRecipe::new));

    @Override
    public MapCodec<TShapedRecipe> codec() { return CODEC; }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, TShapedRecipe> streamCodec() {
        return StreamCodec.of(this::toNetwork, this::fromNetwork);
    }

    private void toNetwork(RegistryFriendlyByteBuf buf, TShapedRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeUtf(recipe.category().getSerializedName());
        ShapedRecipe.Serializer.STREAM_CODEC.encode(buf, recipe.internal());
        ItemStack.STREAM_CODEC.encode(buf, recipe.result());
        CompoundTag tag = (CompoundTag) RecipeHandle.CODEC.encodeStart(NbtOps.INSTANCE, recipe.handle()).getOrThrow();
        ByteBufCodecs.COMPOUND_TAG.encode(buf, tag);
    }

    private TShapedRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
        String group = buf.readUtf();
        String catName = buf.readUtf();
        CraftingBookCategory category = CraftingBookCategory.valueOf(catName.toUpperCase(java.util.Locale.ROOT));
        ShapedRecipe internal = ShapedRecipe.Serializer.STREAM_CODEC.decode(buf);
        ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
        CompoundTag tag = ByteBufCodecs.COMPOUND_TAG.decode(buf);
        RecipeHandle handle = RecipeHandle.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        return new TShapedRecipe(group, category, internal, result, handle);
    }
}