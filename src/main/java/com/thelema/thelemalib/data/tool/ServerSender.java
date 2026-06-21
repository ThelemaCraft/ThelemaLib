package com.thelema.thelemalib.data.tool;

import com.thelema.thelemalib.data.pack.AddToListPack;
import com.thelema.thelemalib.data.pack.AddToListPack2;
import com.thelema.thelemalib.data.pack.PutPack;
import com.thelema.thelemalib.data.pack.RemoveFromListPack;
import com.thelema.thelemalib.data.pack.RemoveFromListPack2;
import com.thelema.thelemalib.data.pack.RemovePack;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class ServerSender {

    // ========== 覆盖整个键值 ==========
    public static void put(String key, Tag value) {
        PacketDistributor.sendToAllPlayers(new PutPack(key, value));
    }

    public static void put(ServerPlayer player, String key, Tag value) {
        PacketDistributor.sendToPlayer(player, new PutPack(key, value));
    }

    public static void remove(String key) {
        PacketDistributor.sendToAllPlayers(new RemovePack(key));
    }

    public static void remove(ServerPlayer player, String key) {
        PacketDistributor.sendToPlayer(player, new RemovePack(key));
    }

    // ========== 列表操作：单个元素 ==========
    public static void addToList(String key, Tag value) {
        PacketDistributor.sendToAllPlayers(new AddToListPack(key, value));
    }

    public static void addToList(ServerPlayer player, String key, Tag value) {
        PacketDistributor.sendToPlayer(player, new AddToListPack(key, value));
    }

    public static void removeFromList(String key, Tag value) {
        PacketDistributor.sendToAllPlayers(new RemoveFromListPack(key, value));
    }

    public static void removeFromList(ServerPlayer player, String key, Tag value) {
        PacketDistributor.sendToPlayer(player, new RemoveFromListPack(key, value));
    }

    // ========== 列表操作：批量元素 ==========
    public static void addAllToList(String key, ListTag values) {
        PacketDistributor.sendToAllPlayers(new AddToListPack2(key, values));
    }

    public static void addAllToList(ServerPlayer player, String key, ListTag values) {
        PacketDistributor.sendToPlayer(player, new AddToListPack2(key, values));
    }

    public static void removeAllFromList(String key, ListTag values) {
        PacketDistributor.sendToAllPlayers(new RemoveFromListPack2(key, values));
    }

    public static void removeAllFromList(ServerPlayer player, String key, ListTag values) {
        PacketDistributor.sendToPlayer(player, new RemoveFromListPack2(key, values));
    }
}