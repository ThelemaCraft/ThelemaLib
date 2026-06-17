package com.thelema.thelemalib.recipe.serializer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelema.thelemalib.recipe.RecipeHandle;
import com.thelema.thelemalib.recipe.type.TAbstractCookingRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;

public class TCookingSerializer<T extends TAbstractCookingRecipe> implements RecipeSerializer<T> {
    private final Function<Args, T> factory;
    private final MapCodec<T> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

    // 公开以便外部访问
    public record Args(String group, CookingBookCategory category, Ingredient ingredient,
                       Optional<ItemStack> template, float experience, int cookingTime, RecipeHandle handle) {}

    public TCookingSerializer(Function<Args, T> factory) {
        this.factory = factory;
        this.codec = RecordCodecBuilder.<Args>mapCodec(inst -> inst.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(Args::group),
                CookingBookCategory.CODEC.optionalFieldOf("category", CookingBookCategory.MISC).forGetter(Args::category),
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Args::ingredient),
                ItemStack.CODEC.optionalFieldOf("result").forGetter(Args::template),
                Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(Args::experience),
                Codec.INT.optionalFieldOf("cookingtime", 200).forGetter(Args::cookingTime),
                RecipeHandle.CODEC.optionalFieldOf("handle", RecipeHandle.EMPTY).forGetter(Args::handle)
        ).apply(inst, Args::new)).xmap(
                args -> factory.apply(args),
                recipe -> new Args(
                        recipe.getGroup(),
                        recipe.category(),
                        recipe.getIngredients().get(0),
                        Optional.of(recipe.getResultItem(null)), // result
                        recipe.getExperience(),
                        recipe.getCookingTime(),
                        recipe.getHandle()
                )
        );

        this.streamCodec = StreamCodec.of(this::toNetwork, this::fromNetwork);
    }

    private void toNetwork(RegistryFriendlyByteBuf buf, T recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeEnum(recipe.category());
        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.getIngredients().get(0));
        ItemStack.STREAM_CODEC.encode(buf, recipe.getResultItem(null));
        buf.writeFloat(recipe.getExperience());
        buf.writeVarInt(recipe.getCookingTime());
        RecipeHandle.STREAM_CODEC.encode(buf, recipe.getHandle());
    }

    private T fromNetwork(RegistryFriendlyByteBuf buf) {
        String group = buf.readUtf();
        CookingBookCategory category = buf.readEnum(CookingBookCategory.class);
        Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
        ItemStack template = ItemStack.STREAM_CODEC.decode(buf);
        float experience = buf.readFloat();
        int cookingTime = buf.readVarInt();
        RecipeHandle handle = RecipeHandle.STREAM_CODEC.decode(buf);
        return factory.apply(new Args(group, category, ingredient, Optional.of(template), experience, cookingTime, handle));
    }

    @Override
    public @NotNull MapCodec<T> codec() { return codec; }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() { return streamCodec; }

}