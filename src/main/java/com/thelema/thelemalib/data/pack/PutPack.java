package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record PutPack(String mapName, String key, CompoundTag value) implements CustomPacketPayload {
    public static final Type<PutPack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "put"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, PutPack> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeUtf(pkt.mapName).writeUtf(pkt.key).writeNbt(pkt.value),
        buf -> new PutPack(buf.readUtf(), buf.readUtf(), buf.readNbt())
    );

    public static void handle(PutPack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<String, Tag> map = ClientCache.MAP_CACHE.computeIfAbsent(pkt.mapName, s -> new HashMap<>());
            // 解包：从 CompoundTag 中取出 "v" 键对应的真实 Tag
            Tag actual = pkt.value.get("v");
            // 若没有 "v" 键，则直接存储整个 CompoundTag（兼容未包装的情况）
            map.put(pkt.key, Objects.requireNonNullElseGet(actual, () -> pkt.value));
            return map;
        });
    }
}