package com.thelema.thelemalib.data.tool;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public record MapSyncPack(String dimId, String file, CompoundTag tag) implements CustomPacketPayload {
    public static final Type<MapSyncPack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "map_sync"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, MapSyncPack> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.dimId);
            buf.writeUtf(pkt.file);
            buf.writeNbt(pkt.tag);
        },
        buf -> new MapSyncPack(buf.readUtf(), buf.readUtf(), buf.readNbt())
    );

    public static void handle(MapSyncPack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 1. 反序列化 NBT 为 Map
            HolderLookup.Provider provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            Map<Object, Object> map = MapConverter.fromNBT(pkt.tag, provider);

            // 2. 直接写入客户端缓存
            LevelMapClient.CACHE
                    .computeIfAbsent(pkt.dimId, k -> new ConcurrentHashMap<>())
                    .put(pkt.file, map);
        });
    }
}