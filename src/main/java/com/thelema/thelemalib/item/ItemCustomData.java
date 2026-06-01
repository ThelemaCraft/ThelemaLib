package com.thelema.thelemalib.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;


/**对物品栈CustomData操作的小工具*/
public class ItemCustomData {

    /**
     * 1：获取 ItemStack 的 通用 NBT。
     * <p>
     * 2：请使用 set 方法保存
     * </p>
     */
    public static CompoundTag get(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /**
     * 保存
     */
    public static void set(ItemStack stack, CompoundTag set) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(set.copy()));
    }

    /**
     * 修改通用 NBT，自动创建和保存，是最常用的方法
     */
    public static void update(ItemStack stack, Consumer<CompoundTag> modifier) {
        CompoundTag tag = get(stack);
        modifier.accept(tag);
        set(stack, tag);
    }

    /**复制 set 的NBT*/
    public static void copy(ItemStack target, ItemStack source){
        set(target, get(source));
    }

    /**布尔数据判断*/
    public static boolean hasFlag(ItemStack stack, String flag){
        CompoundTag orCreate = get(stack);
        return orCreate.contains(flag) && orCreate.getBoolean(flag);
    }

    /**设置布尔*/
    public static void setFlag(ItemStack stack, String flag, boolean b){
        update(stack, tag -> tag.putBoolean(flag, b));
    }
}
