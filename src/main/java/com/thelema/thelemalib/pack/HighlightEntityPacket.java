package com.thelema.thelemalib.pack;

import com.thelema.thelemalib.ThelemaLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


public record HighlightEntityPacket(int entityId, boolean add) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ThelemaLib.MOD_ID, "highlight_entity");
    public static final Type<HighlightEntityPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, HighlightEntityPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HighlightEntityPacket::entityId,
            ByteBufCodecs.BOOL, HighlightEntityPacket::add,
            HighlightEntityPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}