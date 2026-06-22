package com.thelema.thelemalib.block;

import com.thelema.thelemalib.data.LevelMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BreakManager {

    public static final String KEY = "thelemalib_break_manager";
    public static final int MAX = 99;

    private final LevelMap<BlockPos, Integer> map;

    private BreakManager(LevelMap<BlockPos, Integer> map) {
        this.map = map;
    }

    public static BreakManager get(ServerLevel level) {
        return new BreakManager(LevelMap.common(level, KEY));
    }

    /**
     * 玩家/实体挖掘方块，每次增加破坏值
     */
    public void dig(BlockPos pos, ItemStack tool, LivingEntity breaker) {
        ServerLevel level = map.level;
        BlockState state = level.getBlockState(pos);
        int damage = (int) (tool.getDestroySpeed(state) * 6);  // 铁镐→30

        boolean needSync = false;
        Integer current = map.get(pos);
        if (current == null) {
            map.put(pos, damage);
            if (damage >= 10) needSync = true;
        } else {
            int newDamage = current + damage;
            map.put(pos, newDamage);
            // 正确检测跨阶段（十位数字变化）
            if (newDamage / 10 > current / 10) {
                needSync = true;
            }
        }

        // 检查是否超过最大值
        if (map.get(pos) > MAX) {
            // 原版 destroyBlock 会自动处理掉落（基于工具正确性）、音效、粒子
            level.destroyBlock(pos, tool.isCorrectToolForDrops(state), breaker);
            // 清除裂纹
            clearCrack(pos);
            map.remove(pos);
        } else if (needSync) {
            sync(pos);
        }
    }

    /**
     * 直接设置破坏进度（不经过挖掘计算）
     *
     * @param drop 是否强制掉落（不受工具限制）
     */
    public void set(BlockPos pos, int n, boolean drop) {
        ServerLevel level = map.level;

        if (n > MAX) {
            BlockState state = level.getBlockState(pos);
            // 销毁方块，不自动掉落（手动控制）
            level.destroyBlock(pos, false, null);
            if (drop) {
                Block.getDrops(state, level, pos, null, null, ItemStack.EMPTY)
                        .forEach(stack -> Block.popResource(level, pos, stack));
            }
            clearCrack(pos);
            map.remove(pos);
        } else if (n > 0) {
            map.put(pos, n);
            sync(pos);
        }
    }

    /**
     * 同步裂纹动画给附近所有玩家
     */
    public void sync(BlockPos pos) {
        Integer val = map.get(pos);
        if (val == null) return;
        int stage = Math.max(0, Math.min(9, val / 10));  // 0~9
        ClientboundBlockDestructionPacket packet = new ClientboundBlockDestructionPacket(-1, pos, stage);
        ServerLevel level = map.level;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= 1024) {
                player.connection.send(packet);
            }
        }
    }

    private void clearCrack(BlockPos pos) {
        ServerLevel level = map.level;
        ClientboundBlockDestructionPacket packet = new ClientboundBlockDestructionPacket(-1, pos, -1);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= 1024) {
                player.connection.send(packet);
            }
        }
    }
}