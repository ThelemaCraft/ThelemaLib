package com.thelema.thelemalib.data.tool;

import com.thelema.thelemalib.ThelemaLib;
import net.minecraft.nbt.CompoundTag;
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
public record SyncLevelMapPacket(String file, CompoundTag tag) implements CustomPacketPayload {
    public static final Type<SyncLevelMapPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "sync_level_map"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, SyncLevelMapPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.file);
            buf.writeNbt(pkt.tag);
        },
        buf -> new SyncLevelMapPacket(buf.readUtf(), buf.readNbt())
    );

    public static void handle(SyncLevelMapPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> LevelMapClientCache.put(pkt.file, pkt.tag));
    }

    @SubscribeEvent
    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ThelemaLib.MOD_ID);
        registrar.playToClient(
                SyncLevelMapPacket.TYPE,
                SyncLevelMapPacket.STREAM_CODEC,
                SyncLevelMapPacket::handle
        );
    }
}