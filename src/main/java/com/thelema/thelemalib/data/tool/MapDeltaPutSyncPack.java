package com.thelema.thelemalib.data.tool;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record MapDeltaPutSyncPack(
    String dimId,
    String file,
    List<String> keyPath,
    String valueTypeId,
    CompoundTag valueTag
) implements CustomPacketPayload {

    public static final Type<MapDeltaPutSyncPack> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "map_delta_put_sync"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, MapDeltaPutSyncPack> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.dimId);
            buf.writeUtf(pkt.file);
            buf.writeInt(pkt.keyPath.size());
            for (String key : pkt.keyPath) buf.writeUtf(key);
            buf.writeUtf(pkt.valueTypeId);
            buf.writeNbt(pkt.valueTag);
        },
        buf -> {
            String dimId = buf.readUtf();
            String file = buf.readUtf();
            int size = buf.readInt();
            List<String> path = new ArrayList<>();
            for (int i = 0; i < size; i++) path.add(buf.readUtf());
            String valueTypeId = buf.readUtf();
            CompoundTag tag = buf.readNbt();
            return new MapDeltaPutSyncPack(dimId, file, path, valueTypeId, tag);
        }
    );

    public static void handle(MapDeltaPutSyncPack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<String, Map<Object, Object>> dimCache =
                    LevelMapClient.CACHE.computeIfAbsent(pkt.dimId, k -> new ConcurrentHashMap<>());

            // 递归定位到父级 Map，每一步都将编码字符串还原为实际键对象
            Map<Object, Object> current = dimCache.computeIfAbsent(pkt.file, k -> new ConcurrentHashMap<>());
            for (int i = 0; i < pkt.keyPath.size() - 1; i++) {
                Object key = MapConverter.decodeKey(pkt.keyPath.get(i)); // 还原键对象
                Object child = current.get(key);
                if (!(child instanceof Map)) {
                    child = new HashMap<>();
                    current.put(key, child);
                }
                current = (Map<Object, Object>) child;
            }
            // 最后一个键也需还原
            Object lastKey = MapConverter.decodeKey(pkt.keyPath.get(pkt.keyPath.size() - 1));
            HolderLookup.Provider provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            Tag realTag = pkt.valueTag.contains("_value") ? pkt.valueTag.get("_value") : pkt.valueTag;
            Object value = MapConverter.decodeValue(realTag, pkt.valueTypeId, provider);
            current.put(lastKey, value);
        });
    }
}