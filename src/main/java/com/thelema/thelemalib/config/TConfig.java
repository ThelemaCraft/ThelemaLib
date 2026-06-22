package com.thelema.thelemalib.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class TConfig {
    public static final ModConfigSpec SPEC;
    public static final TConfig CONFIG;

    static {
        Pair<TConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(TConfig::new);
        SPEC = pair.getRight();
        CONFIG = pair.getLeft();
    }

    public final ModConfigSpec.BooleanValue openHurtFix;
    public final ModConfigSpec.ConfigValue<List<? extends String>> tooltipBlacklist;

    TConfig(ModConfigSpec.Builder builder) {
        builder.comment("ThelemaLib 模组配置").push("general");

        openHurtFix = builder
                .comment("当实体受到 0伤害时，取消红色硬直特效")
                .define("openHurtFix", true);

        builder.pop(); // 退出 general
        builder.push("tooltip"); // 新建 tooltip 分类

        tooltipBlacklist = builder
                .comment(
                        "工具提示注入黑名单（屏蔽特定物品或标签的自动提示）",
                        "格式：",
                        "  - 物品ID: 'minecraft:diamond'",
                        "  - 标签ID（需加 tag. 前缀）: 'tag.minecraft.logs'",
                        "示例：['minecraft:diamond', 'tag.minecraft.planks']"
                )
                .defineList(
                        "blacklist",
                        List.of(), // 默认空列表
                        obj -> obj instanceof String // 每个元素必须是字符串
                );

        builder.pop(); // 退出 tooltip
    }
}