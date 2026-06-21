package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record S2CRemovePacket(String key) implements CustomPacketPayload {
    public static final Type<S2CRemovePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "s2c_remove"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, S2CRemovePacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> buf.writeUtf(pkt.key),
        buf -> new S2CRemovePacket(buf.readUtf())
    );

    public static void handle(S2CRemovePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientCache.RAW_CACHE.remove(pkt.key));
    }

}