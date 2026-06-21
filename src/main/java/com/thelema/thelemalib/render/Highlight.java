package com.thelema.thelemalib.render;

import com.thelema.thelemalib.data.LevelMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Highlight {

    static Map<String, List<Entity>> map(ServerLevel level){
        return (Map<String, List<Entity>>) LevelMap.temp(level).map();
    }

    // 分组
    public static void group(String groupName, List<Entity> entities){
        Map<String, List<Entity>> map = map((ServerLevel) entities.getFirst().level());
        map.put(groupName, entities);
    }

    // 高亮，而不分组方法
    public static void light(ServerLevel level, String observer_group, String target_group){
        Map<String, List<Entity>> map = map(level);

        List<Entity> o = map.getOrDefault(observer_group, new ArrayList<>());
        List<Entity> t = map.getOrDefault(target_group, new ArrayList<>());
        for (Entity e : o) {
            // 对玩家操作
            if (e instanceof ServerPlayer player){
                // 目标添加高亮
                t.forEach(t1 -> PacketDistributor.sendToPlayer(player, new HighlightEntityPacket(t1.getId(), true)));
            }
        }
    }

    public static void unlight(ServerLevel level, String observer_group, String target_group){
        Map<String, List<Entity>> map = map(level);

        List<Entity> o = map.getOrDefault(observer_group, new ArrayList<>());
        List<Entity> t = map.getOrDefault(target_group, new ArrayList<>());
        for (Entity e : o) {
            // 对玩家操作
            if (e instanceof ServerPlayer player){
                // 取消对目标的高亮
                t.forEach(t1 -> PacketDistributor.sendToPlayer(player, new HighlightEntityPacket(t1.getId(), false)));
            }
        }
    }

}
