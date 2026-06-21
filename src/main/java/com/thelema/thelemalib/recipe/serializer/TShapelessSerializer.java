package com.thelema.thelemalib.recipe.serializer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelema.thelemalib.register.ItemRegister;
import com.thelema.thelemalib.recipe.tool.JsonCodec;
import com.thelema.thelemalib.recipe.type.TShapelessRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.List;
import java.util.Optional;

public class TShapelessSerializer implements RecipeSerializer<TShapelessRecipe> {

    public static final Codec<ItemStack> RESULT_CODEC = Codec.either(
            Codec.STRING,
            ItemStack.CODEC
    ).xmap(
            either -> either.map(
                    id -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))),
                    stack -> stack
            ),
            Either::right
    );

    public static final MapCodec<TShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Ingredient.CODEC.listOf().xmap(
                    list -> {
                        NonNullList<Ingredient> ingredients = NonNullList.create();
                        ingredients.addAll(list);
                        return ingredients;
                    },
                    List::copyOf
            ).fieldOf("ingredients").forGetter(TShapelessRecipe::ingredients),
            RESULT_CODEC.optionalFieldOf("result").forGetter(r -> {
                ItemStack template = r.template();
                // 仅当模板为 NO_TEMPLATE 时才省略字段
                return template.getItem() == ItemRegister.NO_TEMPLATE.get()
                        ? Optional.empty()
                        : Optional.of(template);
            }),
            JsonCodec.JSON_ARRAY_CODEC.optionalFieldOf("handle", new JsonArray()).forGetter(TShapelessRecipe::handle)
    ).apply(inst, (ingredients, templateOpt, handle) -> {
        ItemStack template = templateOpt.orElse(new ItemStack(ItemRegister.NO_TEMPLATE));
        return new TShapelessRecipe(ingredients, template, handle);
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, TShapelessRecipe> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TShapelessRecipe decode(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            NonNullList<Ingredient> ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
            for (int i = 0; i < size; i++) {
                ingredients.set(i, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
            }
            ItemStack template = ItemStack.STREAM_CODEC.decode(buf);
            String jsonStr = ByteBufCodecs.STRING_UTF8.decode(buf);
            JsonElement handleElem = JsonParser.parseString(jsonStr);
            JsonArray handle = handleElem.isJsonArray() ? handleElem.getAsJsonArray() : new JsonArray();
            return new TShapelessRecipe(ingredients, template, handle);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, TShapelessRecipe recipe) {
            buf.writeVarInt(recipe.ingredients().size());
            for (Ingredient ing : recipe.ingredients()) {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ing);
            }
            ItemStack.STREAM_CODEC.encode(buf, recipe.template());
            ByteBufCodecs.STRING_UTF8.encode(buf, recipe.handle().toString());
        }
    };

    @Override
    public MapCodec<TShapelessRecipe> codec() { return CODEC; }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, TShapelessRecipe> streamCodec() { return STREAM_CODEC; }
}