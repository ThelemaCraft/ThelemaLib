package com.thelema.thelemalib.data.pack;

import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.data.tool.ClientCache;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record RemoveFromListPack(String key, Tag value) implements CustomPacketPayload {
    public static final Type<RemoveFromListPack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("thelemalib", "put"));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final StreamCodec<FriendlyByteBuf, RemoveFromListPack> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeUtf(pkt.key);
            buf.writeNbt(pkt.value);
        },
        buf -> new RemoveFromListPack(buf.readUtf(), buf.readNbt())
    );

    public static void handle(RemoveFromListPack pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 有则遍历删除
            if (ClientCache.RAW_CACHE.containsKey(pkt.key)){
                Tag tag = ClientCache.RAW_CACHE.get(pkt.key);
                if (tag instanceof ListTag listTag){
                    // 移除 String形式相等的
                    listTag.removeIf(t -> t.getAsString().equals(pkt.value().getAsString()));
                }else {
                    ThelemaLib.LOGGER.error("RemoveFromListPack, tag instanceof ListTag listTag is false!");
                }
            }
        });
    }

}