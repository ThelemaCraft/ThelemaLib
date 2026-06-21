package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AddToListPack2(String key, ListTag values) implements CustomPacketPayload {
    public static final Type<AddToListPack2> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "add_list2"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, AddToListPack2> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.key);
            // 将 ListTag 包装在 CompoundTag 中
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
            return new AddToListPack2(key, list);
        }
    );

    public static void handle(AddToListPack2 pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ListTag values = pkt.values;
            if (values == null || values.isEmpty()) return;

            if (!ClientCache.RAW_CACHE.containsKey(pkt.key)) {
                ListTag newList = new ListTag();
                newList.addAll(values);
                ClientCache.RAW_CACHE.put(pkt.key, newList);
            } else {
                Tag tag = ClientCache.RAW_CACHE.get(pkt.key);
                if (tag instanceof ListTag existing) {
                    existing.addAll(values);
                }
            }
        });
    }
}