package com.thelema.thelemalib.generic;

import com.thelema.thelemalib.tip.ToolTip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class NoTemplateItem extends Item {

    public NoTemplateItem() {
        super(new Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tip, TooltipFlag tooltipFlag) {
        new ToolTip(tip)
                .trans("item.thelemalib.no_template.desc").color(ChatFormatting.GREEN).build();
    }
}
