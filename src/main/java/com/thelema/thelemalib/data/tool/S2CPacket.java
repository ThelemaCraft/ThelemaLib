package com.thelema.thelemalib.data.tool;

import com.thelema.thelemalib.ThelemaLib;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ThelemaLib.MOD_ID)
public record S2CPacket(String key, Tag value) implements CustomPacketPayload {
    public static final Type<S2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "s2c"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, S2CPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.key);
            buf.writeNbt(pkt.value);
        },
        buf -> new S2CPacket(buf.readUtf(), buf.readNbt())
    );

    public static void handle(S2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientCache.RAW_CACHE.put(pkt.key, pkt.value));
    }

    @SubscribeEvent
    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ThelemaLib.MOD_ID);
        registrar.playToClient(
                S2CPacket.TYPE,
                S2CPacket.STREAM_CODEC,
                S2CPacket::handle
        );
    }
}