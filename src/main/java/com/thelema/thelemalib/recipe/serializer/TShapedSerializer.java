package com.thelema.thelemalib.recipe.serializer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelema.thelemalib.recipe.tool.JsonCodec;
import com.thelema.thelemalib.recipe.type.TShapedRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import java.util.Optional;

public class TShapedSerializer implements RecipeSerializer<TShapedRecipe> {

    // 兼容 "result": "minecraft:iron_sword" 或 "result": {"id":"...","count":1}
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

    public static final MapCodec<TShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            ShapedRecipePattern.MAP_CODEC.forGetter(TShapedRecipe::pattern),
            RESULT_CODEC.optionalFieldOf("result").forGetter(r -> {
                ItemStack template = r.template();
                return template.isEmpty() || template.getItem() == Items.STRUCTURE_VOID ?
                        Optional.empty() : Optional.of(template);
            }),
            JsonCodec.JSON_ARRAY_CODEC.optionalFieldOf("handle", new JsonArray()).forGetter(TShapedRecipe::handle)
    ).apply(inst, (pattern, templateOpt, handle) -> {
        ItemStack template = templateOpt.orElse(new ItemStack(Items.STRUCTURE_VOID));
        return new TShapedRecipe(pattern, template, handle);
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, TShapedRecipe> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TShapedRecipe decode(RegistryFriendlyByteBuf buf) {
            ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buf);
            ItemStack template = ItemStack.STREAM_CODEC.decode(buf);
            String jsonStr = ByteBufCodecs.STRING_UTF8.decode(buf);
            JsonElement handleElem = com.google.gson.JsonParser.parseString(jsonStr);
            JsonArray handle = handleElem.isJsonArray() ? handleElem.getAsJsonArray() : new JsonArray();
            return new TShapedRecipe(pattern, template, handle);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, TShapedRecipe recipe) {
            ShapedRecipePattern.STREAM_CODEC.encode(buf, recipe.pattern());
            ItemStack.STREAM_CODEC.encode(buf, recipe.template());
            ByteBufCodecs.STRING_UTF8.encode(buf, recipe.handle().toString());
        }
    };

    @Override
    public MapCodec<TShapedRecipe> codec() { return CODEC; }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, TShapedRecipe> streamCodec() { return STREAM_CODEC; }
}