package com.thelema.thelemalib.item;

import net.minecraft.world.item.ItemStack;

/**
 * 基于剩余耐久的操作接口，屏蔽原版「损失值」的复杂性。
 */
public interface Durability {

    /**
     * 获取当前剩余耐久值。无耐久返回 int极限。
     */
    static int get(ItemStack stack) {
        if (stack.isEmpty()) return Integer.MAX_VALUE;
        int max = stack.getMaxDamage();
        int damage = stack.getDamageValue();
        return max - damage;
    }

    /**
     * 设置剩余耐久值（钳位在 0 ~ 最大耐久之间）。
     */
    static void set(ItemStack stack, int remaining) {
        if (stack.isEmpty()) return;
        int max = stack.getMaxDamage();
        int newDamage = Math.max(0, max - Math.min(max, remaining));
        stack.setDamageValue(newDamage);
    }

    /**
     * 当前剩余耐久比例（0.0 ~ 1.0）。
     */
    static float getFraction(ItemStack stack) {
        if (stack.isEmpty()) return 0f;
        return (float) get(stack) / (float) stack.getMaxDamage();
    }

    /**
     * 判断物品是否已损坏（剩余耐久为 0）。
     */
    static boolean isBroken(ItemStack stack) {
        return get(stack) <= 0;
    }

    /**
     * 减少指定量的耐久（不会低于 0）。
     */
    static void damage(ItemStack stack, int amount) {
        if (stack.isEmpty()) return;
        int current = get(stack);
        set(stack, Math.max(0, current - amount));
    }

    /**
     * 增加指定量的耐久（不会超过最大耐久）。
     */
    static void repair(ItemStack stack, int amount) {
        if (stack.isEmpty()) return;
        int current = get(stack);
        set(stack, Math.min(stack.getMaxDamage(), current + amount));
    }
}