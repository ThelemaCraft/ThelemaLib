package com.thelema.thelemalib.block;

import com.thelema.thelemalib.data.BlockMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class BreakManager {

    public static final String KEY = "thelemalib_break_manager";
    public static final int MAX = 99;

    public BlockMap<Integer> map;

    private BreakManager(BlockMap<Integer> map){
        this.map = map;
    }

    public static BreakManager get(ServerLevel level) {
        return new BreakManager(BlockMap.get(level, KEY));
    }

    /**
     * 玩家/实体挖掘方块，每次增加破坏值
     */
    public void dig(BlockPos pos, ItemStack tool, LivingEntity breaker) {
        Map<BlockPos, Integer> progress = map.map();
        ServerLevel level = map.level();

        BlockState state = level.getBlockState(pos);
        int damage = (int) (tool.getDestroySpeed(state) * 6);  // 铁镐→30

        boolean needSync = false;
        if (!progress.containsKey(pos)) {
            progress.put(pos, damage);
            if (damage >= 10) needSync = true;
        } else {
            int current = progress.get(pos);
            int newDamage = current + damage;
            progress.put(pos, newDamage);
            // 正确检测跨阶段（十位数字变化）
            if (newDamage / 10 > current / 10) {
                needSync = true;
            }
        }
        setDirty();

        // 检查是否超过最大值
        if (progress.get(pos) > MAX) {
            // 原版 destroyBlock 会自动处理掉落（基于工具正确性）、音效、粒子
            level.destroyBlock(pos, tool.isCorrectToolForDrops(state), breaker);
            // 清除裂纹
            clearCrack(pos);
            progress.remove(pos);
            setDirty();
        } else if (needSync) {
            sync(pos);
        }
    }

    /**
     * 直接设置破坏进度（不经过挖掘计算）
     * @param drop 是否强制掉落（不受工具限制）
     */
    public void set(BlockPos pos, int n, boolean drop) {
        Map<BlockPos, Integer> progress = map.map();
        ServerLevel level = map.level();

        if (n > MAX) {
            BlockState state = level.getBlockState(pos);
            // 销毁方块，不自动掉落（手动控制）
            level.destroyBlock(pos, false, null);
            if (drop) {
                Block.getDrops(state, level, pos, null, null, ItemStack.EMPTY)
                        .forEach(stack -> Block.popResource(level, pos, stack));
            }
            clearCrack(pos);
            progress.remove(pos);
            setDirty();
        } else if (n > 0) {
            progress.put(pos, n);
            setDirty();
            sync(pos);
        }
    }

    /**
     * 同步裂纹动画给附近所有玩家
     */
    public void sync(BlockPos pos) {
        Map<BlockPos, Integer> progress = map.map();
        ServerLevel level = map.level();

        Integer val = progress.get(pos);
        if (val == null) return;
        int stage = Math.max(0, Math.min(9, val / 10));  // 0~9
        ClientboundBlockDestructionPacket packet = new ClientboundBlockDestructionPacket(-1, pos, stage);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= 1024) {
                player.connection.send(packet);
            }
        }
    }

    private void clearCrack(BlockPos pos) {
        ServerLevel level = map.level();

        ClientboundBlockDestructionPacket packet = new ClientboundBlockDestructionPacket(-1, pos, -1);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= 1024) {
                player.connection.send(packet);
            }
        }
    }

    public void setDirty() {
        map.setDirty();
    }
}