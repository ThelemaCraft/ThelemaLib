package com.thelema.thelemalib;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thelema.thelemalib.data.MapHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;

@EventBusSubscriber() // 替换为您的modid
public class TestCommand {

    private static final List<TestEntry> TEST_ENTRIES = new ArrayList<>();
    private static int testCounter = 0;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 初始化所有测试用例
        initTestEntries();

        var command = Commands.literal("tltest");

        for (TestEntry entry : TEST_ENTRIES) {
            command = command.then(
                    Commands.literal(entry.id)
                            .executes(ctx -> runTest(ctx, entry))
            );
        }

        dispatcher.register(command);
    }

    private static void initTestEntries() {
        TEST_ENTRIES.clear();

        // 测试用例1: String->String
        TEST_ENTRIES.add(new TestEntry(
                "1",
                "String->String",
                () -> {
                    Map<Object, Object> map = new HashMap<>();
                    map.put("name", "Alice");
                    return map;
                }
        ));

        // 测试用例2: String->Int
        TEST_ENTRIES.add(new TestEntry(
                "2",
                "String->Int",
                () -> {
                    Map<Object, Object> map = new HashMap<>();
                    map.put("level", 42);
                    return map;
                }
        ));

        // 测试用例3: String->Boolean
        TEST_ENTRIES.add(new TestEntry(
                "3",
                "String->Boolean",
                () -> {
                    Map<Object, Object> map = new HashMap<>();
                    map.put("active", true);
                    return map;
                }
        ));

        // 测试用例4: String->UUID
        TEST_ENTRIES.add(new TestEntry(
                "4",
                "String->UUID",
                () -> {
                    Map<Object, Object> map = new HashMap<>();
                    map.put("uid", UUID.randomUUID());
                    return map;
                }
        ));

        // 测试用例5: String->BlockPos
        TEST_ENTRIES.add(new TestEntry(
                "5",
                "String->BlockPos",
                () -> {
                    Map<Object, Object> map = new HashMap<>();
                    map.put("spawn", new BlockPos(10, 64, 10));
                    return map;
                }
        ));

        // 测试用例6: String->Vec3
        TEST_ENTRIES.add(new TestEntry(
                "6",
                "String->Vec3",
                () -> {
                    Map<Object, Object> map = new HashMap<>();
                    map.put("velocity", new Vec3(1.5, 2.0, 3.3));
                    return map;
                }
        ));

        // 测试用例7: String->ItemStack
        TEST_ENTRIES.add(new TestEntry(
                "7",
                "String->ItemStack",
                () -> {
                    Map<Object, Object> map = new HashMap<>();
                    map.put("weapon", new ItemStack(Items.DIAMOND_SWORD, 1));
                    return map;
                }
        ));

        // 测试用例8: String->Map
        TEST_ENTRIES.add(new TestEntry(
                "8",
                "String->Map",
                () -> {
                    Map<Object, Object> inner = new HashMap<>();
                    inner.put("score", 100);
                    inner.put("name", "test");
                    Map<Object, Object> map = new HashMap<>();
                    map.put("data", inner);
                    return map;
                }
        ));

        // 测试用例9: String->List<BlockPos>
        TEST_ENTRIES.add(new TestEntry(
                "9",
                "String->List<BlockPos>",
                () -> {
                    Map<Object, Object> map = new HashMap<>();
                    List<BlockPos> list = Arrays.asList(new BlockPos(1,2,3), new BlockPos(4,5,6));
                    map.put("waypoints", list);
                    return map;
                }
        ));

        // 测试用例10: BlockPos->Map<Integer,List<UUID>>
        TEST_ENTRIES.add(new TestEntry(
                "10",
                "BlockPos->Map<Integer,List<UUID>>",
                () -> {
                    Map<Object, Object> innerMap = new HashMap<>();
                    List<UUID> uuidList = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
                    innerMap.put(1, uuidList);
                    Map<Object, Object> map = new HashMap<>();
                    map.put(new BlockPos(0, 0, 0), innerMap);
                    return map;
                }
        ));
    }

    private static int runTest(CommandContext<CommandSourceStack> ctx, TestEntry entry) {
        CommandSourceStack source = ctx.getSource();
        HolderLookup.Provider provider = source.getLevel().registryAccess();

        Map<Object, Object> original = entry.supplier.get();

        // 编码
        CompoundTag tag = MapHelper.toNBT(original, provider);
        // 解码
        Map<Object, Object> decoded = MapHelper.fromNbt(tag, provider);

        // 简单比较
        boolean equals = original.equals(decoded);
        String message = "§6[Test " + entry.id + "] §r" + entry.name + " → " + (equals ? "§a✓ PASS" : "§c✗ FAIL");

        if (!equals) {
            message += "\n§7Original: " + original + "\n§7Decoded:  " + decoded;
        }

        source.sendSystemMessage(Component.literal(message));

        // 如果执行者是玩家，也发送聊天消息
        if (source.getEntity() instanceof Player player) {
            player.sendSystemMessage(Component.literal(message));
        }

        return 1;
    }

    // 添加便捷方法：批量运行所有测试
    @SubscribeEvent
    public static void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        // 可选：登录时自动运行测试（需要权限控制）
        // 可以通过配置开关控制
    }

    // 测试条目的数据类
    private static class TestEntry {
        final String id;
        final String name;
        final MapSupplier supplier;

        TestEntry(String id, String name, MapSupplier supplier) {
            this.id = id;
            this.name = name;
            this.supplier = supplier;
        }
    }

    @FunctionalInterface
    interface MapSupplier {
        Map<Object, Object> get();
    }
}