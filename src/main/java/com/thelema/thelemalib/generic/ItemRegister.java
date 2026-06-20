package com.thelema.thelemalib.generic;

import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.tip.ToolTip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ItemRegister {

    static final DeferredRegister.Items ITEM = DeferredRegister.createItems(ThelemaLib.MOD_ID);
    public static void register(IEventBus bus){
        ITEM.register(bus);
    }
    public static DeferredHolder<Item, Item> NO_TEMPLATE;

    static {
        NO_TEMPLATE = ITEM.register("no_template", () -> new Item(new Item.Properties()){
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tip, TooltipFlag tooltipFlag) {
                new ToolTip(tip)
                        .trans("This Recipe has no default output, it's dynamic").color(ChatFormatting.GREEN);
            }
        });
    }

}
