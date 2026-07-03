package com.thelema.thelemalib.data.tool;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record MapDeltaRemoveSyncPack(
    String dimId,
    String file,
    List<String> keyPath
) implements CustomPacketPayload {

    public static final Type<MapDeltaRemoveSyncPack> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "map_delta_remove_sync"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, MapDeltaRemoveSyncPack> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.dimId);
            buf.writeUtf(pkt.file);
            buf.writeInt(pkt.keyPath.size());
            for (String key : pkt.keyPath) buf.writeUtf(key);
        },
        buf -> {
            String dimId = buf.readUtf();
            String file = buf.readUtf();
            int size = buf.readInt();
            List<String> path = new ArrayList<>();
            for (int i = 0; i < size; i++) path.add(buf.readUtf());
            return new MapDeltaRemoveSyncPack(dimId, file, path);
        }
    );

    public static void handle(MapDeltaRemoveSyncPack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<String, Map<Object, Object>> dimCache = LevelMapClient.CACHE.get(pkt.dimId);
            if (dimCache == null) return;
            Map<Object, Object> fileMap = dimCache.get(pkt.file);
            if (fileMap == null) return;

            Map<Object, Object> current = fileMap;
            for (int i = 0; i < pkt.keyPath.size() - 1; i++) {
                Object key = MapConverter.decodeKey(pkt.keyPath.get(i)); // 还原键对象
                Object child = current.get(key);
                if (!(child instanceof Map)) return;
                current = (Map<Object, Object>) child;
            }
            Object lastKey = MapConverter.decodeKey(pkt.keyPath.get(pkt.keyPath.size() - 1));
            current.remove(lastKey);
        });
    }
}