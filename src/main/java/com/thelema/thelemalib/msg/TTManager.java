package com.thelema.thelemalib.msg;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


@EventBusSubscriber
public class TTManager {

    private static final List<Consumer<ItemTooltipEvent>> HANDLERS = new ArrayList<>();

    public static void register(Consumer<ItemTooltipEvent> handler) {
        HANDLERS.add(handler);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        for (Consumer<ItemTooltipEvent> handler : HANDLERS) {
            handler.accept(event);
        }
    }

}