package com.thelema.thelemalib.msg;

import net.minecraft.network.chat.Component;

public class TTExtra {

    private TTExtra() {}

    /**
     * 创建键值对实例，key 和 value 会被自动解析（同 ToolTip.parse 逻辑）。
     */
    public static KeyValue keyValue(Object key, Object value) {
        return new KeyValue(key, value);
    }

    public static final class Emoji {
        public static final Component TABLE_FLIP = Component.literal("(╯°□°）╯︵ ┻━┻");
        public static final Component EYEROLL = Component.literal("(¬_¬)");
        public static final Component STRAIGHT_FACE = Component.literal("(•_•)");
        public static final Component HELPLESS = Component.literal("(；一_一)");
        public static final Component WIDE_EYES = Component.literal("(⊙_⊙)");

        private Emoji() {}
    }

    public interface KVConst {
        String KEY_ENTITY = "tooltip.loticlast.generic.key.entity";
        String KEY_UUID = "tooltip.loticlast.generic.key.uuid";
        String KEY_ITEM = "tooltip.loticlast.generic.key.item";
        String KEY_BLOCK = "tooltip.loticlast.generic.key.block";
        String KEY_LIQUID = "tooltip.loticlast.generic.key.liquid";
        String KEY_CONTENT = "tooltip.loticlast.generic.key.content";
        String KEY_UNKNOWN = "tooltip.loticlast.generic.key.unknown";
        String VALUE_EMPTY = "tooltip.loticlast.generic.value.empty";
    }

}