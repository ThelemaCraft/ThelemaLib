package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RemoveFromListPack2(String key, ListTag values) implements CustomPacketPayload {
    public static final Type<RemoveFromListPack2> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "remove_list2"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, RemoveFromListPack2> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.key);
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("list", pkt.values);
            buf.writeNbt(wrapper);
        },
        buf -> {
            String key = buf.readUtf();
            CompoundTag wrapper = buf.readNbt();
            ListTag list = wrapper != null ? (ListTag) wrapper.get("list") : null;
            if (list == null) {
                throw new IllegalStateException("Missing 'list' tag in wrapper for key: " + key);
            }
            return new RemoveFromListPack2(key, list);
        }
    );

    public static void handle(RemoveFromListPack2 pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ListTag values = pkt.values;
            if (values == null || values.isEmpty()) return;

            Tag tag = ClientCache.RAW_CACHE.get(pkt.key);
            if (!(tag instanceof ListTag existing)) {
                ThelemaLib.LOGGER.warn("RemoveFromListPack2: key '{}' not found or not a ListTag", pkt.key);
                return;
            }

            for (Tag t : values) {
                String str = t.getAsString();
                existing.removeIf(e -> e.getAsString().equals(str));
            }
        });
    }
}