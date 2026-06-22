package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;

public record PutPack(String mapName, String key, Tag value) implements CustomPacketPayload {
    public static final Type<PutPack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "put"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, PutPack> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeUtf(pkt.mapName).writeUtf(pkt.key).writeNbt(pkt.value),
        buf -> new PutPack(buf.readUtf(), buf.readUtf(), buf.readNbt())
    );

    public static void handle(PutPack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientCache.MAP_CACHE.computeIfAbsent(pkt.mapName, s -> new HashMap<>()).put(pkt.key, pkt.value));
    }

}