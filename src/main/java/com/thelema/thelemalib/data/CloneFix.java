package com.thelema.thelemalib.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 自动迁移的实体持久化数据
 * */
@EventBusSubscriber
public class CloneFix {

    // 玩家持久化数据的迁移
    @SubscribeEvent
    public static void migrate(PlayerEvent.Clone event){
        Player original = event.getOriginal();
        Player player = event.getEntity();
        // 获取 原实体持久化数据
        CompoundTag data = original.getPersistentData().copy();

        // 复制数据
        for (String key : data.getAllKeys()) {
            CompoundTag target = player.getPersistentData();
            Tag value = data.get(key);
            if (value != null){
                target.put(key, value);
            }
        }

    }
}
