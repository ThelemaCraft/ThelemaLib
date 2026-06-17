package com.thelema.thelemalib.generic;

import com.thelema.thelemalib.ThelemaLib;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemRegister {

    static DeferredRegister.Items ITEM = DeferredRegister.createItems(ThelemaLib.MOD_ID);

    public static void register(IEventBus bus){
        ITEM.register(bus);
    }

    public static final DeferredItem<Item> ANY_ITEM;

    static {
        ANY_ITEM = ITEM.register("any_item",() -> new Item(new Item.Properties()));
    }
}
