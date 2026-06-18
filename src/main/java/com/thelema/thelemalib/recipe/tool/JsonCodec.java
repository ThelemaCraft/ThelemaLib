package com.thelema.thelemalib.recipe.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class JsonCodec {

    // 通用：接受任意 JSON 节点
    public static final Codec<JsonElement> JSON_ELEMENT_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
            element -> new Dynamic<>(JsonOps.INSTANCE, element)
    );

    // 专门接受 JSON 对象
    public static final Codec<JsonObject> JSON_OBJECT_CODEC = JSON_ELEMENT_CODEC.xmap(
            JsonElement::getAsJsonObject,
            obj -> obj
    );

    // 专门接受 JSON 数组（现在可以直接写 [] 了）
    public static final Codec<JsonArray> JSON_ARRAY_CODEC = JSON_ELEMENT_CODEC.xmap(
            element -> element.isJsonArray() ? element.getAsJsonArray() : new JsonArray(),
            array -> array
    );

    // 网络传输保持不变（因为网络包总是字节流，用字符串没问题）
    public static final StreamCodec<RegistryFriendlyByteBuf, JsonArray> STREAM_CODEC = StreamCodec.of(
            (buf, arr) -> ByteBufCodecs.STRING_UTF8.encode(buf, arr.toString()),
            buf -> JsonParser.parseString(ByteBufCodecs.STRING_UTF8.decode(buf)).getAsJsonArray()
    );
}