package com.thelema.thelemalib.tip;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@EventBusSubscriber
public class TTManager {
    // 特定物品处理器：key = 物品注册名(ResourceLocation字符串)
    private static final Map<String, Consumer<ItemTooltipEvent>> ITEM_HANDLERS = new ConcurrentHashMap<>();
    // 标签处理器：key = TagKey<Item>
    private static final Map<TagKey<Item>, Consumer<ItemTooltipEvent>> TAG_HANDLERS = new ConcurrentHashMap<>();

    // ========== 特定物品注册 ==========

    /** 通过物品注册名字符串注册 */
    public static void item(String itemId, Consumer<ItemTooltipEvent> handler) {
        ITEM_HANDLERS.put(itemId, handler);
    }

    /** 通过 DeferredHolder<Item, ...> 注册 */
    public static void item(DeferredHolder<Item, ? extends Item> holder, Consumer<ItemTooltipEvent> handler) {
        item(holder.getId().toString(), handler);
    }

    /** 通过 Item 实例注册 */
    public static void item(Item item, Consumer<ItemTooltipEvent> handler) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        item(id.toString(), handler);
    }

    // ========== 标签注册 ==========

    /** 通过 TagKey<Item> 注册 */
    public static void tag(TagKey<Item> tag, Consumer<ItemTooltipEvent> handler) {
        TAG_HANDLERS.put(tag, handler);
    }

    /** 通过标签 ResourceLocation 字符串注册（例如 "minecraft:swords"） */
    public static void tag(String tagId, Consumer<ItemTooltipEvent> handler) {
        ResourceLocation loc = ResourceLocation.tryParse(tagId);
        if (loc != null) {
            tag(TagKey.create(BuiltInRegistries.ITEM.key(), loc), handler);
        }
    }

    // ========== 事件处理 ==========

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());

        // 1. 特定物品处理器（如果存在则执行，但不会阻止后续标签处理器的执行）
        Consumer<ItemTooltipEvent> itemHandler = ITEM_HANDLERS.get(id.toString());
        if (itemHandler != null) {
            itemHandler.accept(event);
        }

        // 2. 标签处理器：收集所有匹配的处理器并按注册顺序执行
        List<Consumer<ItemTooltipEvent>> matchedTagHandlers = new ArrayList<>();
        for (Map.Entry<TagKey<Item>, Consumer<ItemTooltipEvent>> entry : TAG_HANDLERS.entrySet()) {
            if (stack.is(entry.getKey())) {
                matchedTagHandlers.add(entry.getValue());
            }
        }

        // 执行匹配的标签处理器（即使前面有特定物品处理器也会执行，实现叠加效果）
        for (Consumer<ItemTooltipEvent> handler : matchedTagHandlers) {
            handler.accept(event);
        }
    }
}