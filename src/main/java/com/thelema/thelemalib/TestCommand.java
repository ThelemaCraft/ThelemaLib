package com.thelema.thelemalib;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thelema.thelemalib.area.AreaManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = ThelemaLib.MOD_ID)
public class TestCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tltest")
                .then(Commands.literal("love_area")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(TestCommand::loveArea)))
        );
    }

    private static int loveArea(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ServerLevel level = ctx.getSource().getLevel();
        Player player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("只有玩家可用"));
            return 0;
        }
        BlockPos center = player.blockPosition();
        BlockPos a = center.offset(-5, 0, -5);
        BlockPos b = center.offset(5, 5, 5);
        AreaManager.add(level, "test", name, a, b);  // ✅ 改成 "test"
        ctx.getSource().sendSuccess(() -> Component.literal("测试区域已创建"), false);
        return 1;
    }

}