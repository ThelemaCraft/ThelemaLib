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

public record PutAllPack(String mapName, int size, List<String> keyList, List<CompoundTag> valueList) implements CustomPacketPayload {
    public static final Type<PutAllPack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "put_all"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, PutAllPack> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.mapName);
            buf.writeInt(pkt.size);
            pkt.keyList.forEach(buf::writeUtf);
            pkt.valueList.forEach(buf::writeNbt);
        },
        buf -> {
            String mapName = buf.readUtf();
            int size = buf.readInt();
            ArrayList<String> keyList = new ArrayList<>();
            ArrayList<CompoundTag> valueList = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                keyList.add(buf.readUtf());
                valueList.add(buf.readNbt());
            }
            return new PutAllPack(mapName, size, keyList, valueList);
        }
    );

    public static void handle(PutAllPack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<String, Tag> map = ClientCache.MAP_CACHE.computeIfAbsent(pkt.mapName, s -> new HashMap<>());
            for (int i = 0; i < pkt.size; i++) {
                CompoundTag wrapper = pkt.valueList.get(i);
                Tag actual = wrapper.get("v");
                map.put(pkt.keyList.get(i), Objects.requireNonNullElse(actual, wrapper));
            }
            return map;
        });
    }
}