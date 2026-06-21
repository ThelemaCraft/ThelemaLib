package com.thelema.thelemalib.data.tool;

import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class ServerSender {

    public static void sendToAll(String key, Tag value) {
        PacketDistributor.sendToAllPlayers(new S2CPacket(key, value));
    }

    public static void sendTo(ServerPlayer player, String key, Tag value) {
        PacketDistributor.sendToPlayer(player, new S2CPacket(key, value));
    }

}
