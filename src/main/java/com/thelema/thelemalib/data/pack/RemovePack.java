package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;


public record RemovePack(String mapName, String key) implements CustomPacketPayload {
    public static final Type<RemovePack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "remove"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, RemovePack> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeUtf(pkt.mapName).writeUtf(pkt.key),
        buf -> new RemovePack(buf.readUtf(), buf.readUtf())
    );

    public static void handle(RemovePack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientCache.MAP_CACHE.computeIfAbsent(pkt.mapName, s -> new HashMap<>()).remove(pkt.key));
    }

}