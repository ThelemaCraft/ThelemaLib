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
import org.jetbrains.annotations.Nullable;

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

    public boolean dig(BlockPos pos, @Nullable ItemStack tool, @Nullable LivingEntity breaker) {
        ServerLevel level = map.level;
        BlockState state = level.getBlockState(pos);

        // 计算挖掘速度：工具为空时使用空手速度（1.0f）
        float speed = (tool != null) ? tool.getDestroySpeed(state) : 1.0f;
        int damage = (int) (speed * 6);
        if (damage < 1) damage = 1; // 保底

        Integer current = map.get(pos);
        int newDamage = (current == null) ? damage : current + damage;
        map.put(pos, newDamage);

        boolean broken = false;
        if (newDamage > MAX) {
            // 检查工具是否正确（工具为空时视为不正确）
            boolean correctTool = tool != null && tool.isCorrectToolForDrops(state);
            level.destroyBlock(pos, correctTool, breaker);
            clearCrack(pos);
            map.remove(pos);
            broken = true;
        } else if (current == null || newDamage / 10 > current / 10) {
            sync(pos);
        }

        return broken;
    }

    public void set(BlockPos pos, int n, boolean drop) {
        ServerLevel level = map.level;
        if (n > MAX) {
            BlockState state = level.getBlockState(pos);
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

    public void sync(BlockPos pos) {
        Integer val = map.get(pos);
        if (val == null) return;
        int stage = Math.max(0, Math.min(9, val / 10));
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