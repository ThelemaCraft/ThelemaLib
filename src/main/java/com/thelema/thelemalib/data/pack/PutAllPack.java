package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record PutAllPack(String mapName, CompoundTag data) implements CustomPacketPayload {
    public static final Type<PutAllPack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "put_all"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, PutAllPack> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeUtf(pkt.mapName);
                buf.writeNbt(pkt.data);  // 只写入一个 CompoundTag
            },
            buf -> {
                String mapName = buf.readUtf();
                CompoundTag data = buf.readNbt();
                if (data == null) data = new CompoundTag();
                return new PutAllPack(mapName, data);
            }
    );

    public static void handle(PutAllPack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<String, Tag> map = ClientCache.MAP_CACHE.computeIfAbsent(pkt.mapName, k -> new java.util.concurrent.ConcurrentHashMap<>());
            // 从 CompoundTag 中提取所有键值对
            for (String key : pkt.data.getAllKeys()) {
                Tag value = pkt.data.get(key);
                if (value != null) {
                    map.put(key, value);
                }
            }
            return null;
        });
    }
}