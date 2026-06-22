package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record RemoveAllPack(String mapName, int size, List<String> keyList) implements CustomPacketPayload {
    public static final Type<RemoveAllPack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "remove_all"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, RemoveAllPack> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.mapName);
            buf.writeInt(pkt.size);
            pkt.keyList.forEach(buf::writeUtf);
        },
        buf -> {
            String mapName = buf.readUtf();
            int size = buf.readInt();
            ArrayList<String> keyList = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                keyList.add(buf.readUtf());
            }
            return new RemoveAllPack(mapName, size, keyList);
        }
    );

    public static void handle(RemoveAllPack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<String, Tag> map = ClientCache.MAP_CACHE.get(pkt.mapName);
            if (map != null) {
                for (String key : pkt.keyList) {
                    map.remove(key);
                }
            }
            return null;
        });
    }
}