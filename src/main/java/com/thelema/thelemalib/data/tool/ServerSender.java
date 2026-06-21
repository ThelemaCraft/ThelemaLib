package com.thelema.thelemalib.data.tool;

import com.thelema.thelemalib.data.pack.S2CPutPacket;
import com.thelema.thelemalib.data.pack.S2CRemovePacket;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class ServerSender {

    public static void put(String key, Tag value) {
        PacketDistributor.sendToAllPlayers(new S2CPutPacket(key, value));
    }

    public static void put(ServerPlayer player, String key, Tag value) {
        PacketDistributor.sendToPlayer(player, new S2CPutPacket(key, value));
    }

    public static void remove(String key){
        PacketDistributor.sendToAllPlayers(new S2CRemovePacket(key));
    }

    public static void remove(ServerPlayer player, String key){
        PacketDistributor.sendToPlayer(player,new S2CRemovePacket(key));
    }

}
