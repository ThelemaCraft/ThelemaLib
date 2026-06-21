package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record AddToListPack(String key, Tag value) implements CustomPacketPayload {
    public static final Type<AddToListPack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "add_to_list"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, AddToListPack> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.key);
            buf.writeNbt(pkt.value);
        },
        buf -> new AddToListPack(buf.readUtf(), buf.readNbt())
    );

    public static void handle(AddToListPack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 没有创建对应的 ListTag
            if (!ClientCache.RAW_CACHE.containsKey(pkt.key)){
                ListTag listTag = new ListTag();
                listTag.add(pkt.value);
                ClientCache.RAW_CACHE.put(pkt.key, listTag);
            }
            // 有直接添加
            else {
                Tag tag = ClientCache.RAW_CACHE.get(pkt.key);
                if (tag instanceof ListTag listTag){
                    listTag.add(pkt.value);
                }
            }

        });
    }

}