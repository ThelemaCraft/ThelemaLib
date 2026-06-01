package com.thelema.thelemalib.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class TConfig {
    public static final ModConfigSpec SPEC;
    public static final TConfig CONFIG;

    static {
        Pair<TConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(TConfig::new);
        SPEC = pair.getRight();
        CONFIG = pair.getLeft();
    }

    public final ModConfigSpec.BooleanValue openHurtFix;

    TConfig(ModConfigSpec.Builder builder) {
        builder.comment("ThelemaLib 模组配置");

        openHurtFix = builder
                .comment("当实体受到 0伤害时，取消红色硬直特效")
                .define("openHurtFix", true);

    }

}
