package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record S2CPutPacket(String key, Tag value) implements CustomPacketPayload {
    public static final Type<S2CPutPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "s2c_put"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, S2CPutPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.key);
            buf.writeNbt(pkt.value);
        },
        buf -> new S2CPutPacket(buf.readUtf(), buf.readNbt())
    );

    public static void handle(S2CPutPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientCache.RAW_CACHE.put(pkt.key, pkt.value));
    }

}