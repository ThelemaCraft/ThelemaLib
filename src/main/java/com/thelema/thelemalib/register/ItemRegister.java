package com.thelema.thelemalib.register;

import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.generic.NoTemplateItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


public class ItemRegister {

    static final DeferredRegister.Items ITEM = DeferredRegister.createItems(ThelemaLib.MOD_ID);
    public static void register(IEventBus bus){
        ITEM.register(bus);
    }
    public static DeferredHolder<Item, Item> NO_TEMPLATE;

    static {
        NO_TEMPLATE = ITEM.register("no_template", NoTemplateItem::new);
    }

}
