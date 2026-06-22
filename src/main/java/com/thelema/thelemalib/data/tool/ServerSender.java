package com.thelema.thelemalib.data.tool;

import com.thelema.thelemalib.data.pack.PutAllPack;
import com.thelema.thelemalib.data.pack.PutPack;
import com.thelema.thelemalib.data.pack.RemoveAllPack;
import com.thelema.thelemalib.data.pack.RemovePack;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ServerSender {

    public String mapName;
    public List<ServerPlayer> playerList;

    public ServerSender(String mapName) {
        this.mapName = mapName;
    }

    public ServerSender addPlayer(ServerPlayer player){
        if (playerList == null) playerList = new ArrayList<>();
        playerList.add(player);
        return this;
    }

    public ServerSender setBroadcast(){
        if (playerList == null) playerList = new ArrayList<>();
        playerList.clear();
        return this;
    }

    // ========== 单条操作 ==========
    public ServerSender put(String key, Tag value) {
        if (playerList == null || playerList.isEmpty()){
            PacketDistributor.sendToAllPlayers(new PutPack(mapName, key, value));
        } else {
            for (ServerPlayer player : playerList) {
                PacketDistributor.sendToPlayer(player, new PutPack(mapName, key, value));
            }
        }
        return this;
    }

    public ServerSender remove(String key) {
        if (playerList == null || playerList.isEmpty()){
            PacketDistributor.sendToAllPlayers(new RemovePack(mapName, key));
        } else {
            for (ServerPlayer player : playerList) {
                PacketDistributor.sendToPlayer(player, new RemovePack(mapName, key));
            }
        }
        return this;
    }

    // ========== 批量 putAll ==========

    public ServerSender putAll(List<String> keys, List<Tag> values) {
        if (keys == null || values == null || keys.size() != values.size() || keys.isEmpty()) return this;
        PutAllPack pack = new PutAllPack(mapName, keys.size(), keys, values);
        if (playerList == null || playerList.isEmpty()) {
            PacketDistributor.sendToAllPlayers(pack);
        } else {
            for (ServerPlayer player : playerList) {
                PacketDistributor.sendToPlayer(player, pack);
            }
        }
        return this;
    }

    // ========== 批量 removeAll ==========
    public ServerSender removeAll(List<String> keys) {
        if (keys == null || keys.isEmpty()) return this;
        RemoveAllPack pack = new RemoveAllPack(mapName, keys.size(), keys);
        if (playerList == null || playerList.isEmpty()) {
            PacketDistributor.sendToAllPlayers(pack);
        } else {
            for (ServerPlayer player : playerList) {
                PacketDistributor.sendToPlayer(player, pack);
            }
        }
        return this;
    }

    public ServerSender removeAll(String... keys) {
        return removeAll(Arrays.asList(keys));
    }

}