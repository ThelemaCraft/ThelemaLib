package com.thelema.thelemalib.msg;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@EventBusSubscriber
public class TTManager {
    // 用 ConcurrentHashMap 支持并发，键为物品的 ResourceLocation（字符串形式）
    private static final Map<String, Consumer<ItemTooltipEvent>> HANDLERS = new ConcurrentHashMap<>();

    /**
     * 注册针对特定物品的 Tooltip 处理器
     * @param itemId 物品注册名，如 "minecraft:diamond"
     */
    public static void register(String itemId, Consumer<ItemTooltipEvent> handler) {
        HANDLERS.put(itemId, handler);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        // 直接通过物品 ID 获取并执行对应处理器，无需遍历
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        Consumer<ItemTooltipEvent> handler = HANDLERS.get(id.toString());
        if (handler != null) {
            handler.accept(event);
        }
    }
}