package com.thelema.thelemalib.tip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * 键值对工具，支持分别设置 key、分隔符、value 的样式。
 * 使用示例：
 * <pre>
 * KeyValue kv = new KeyValue("生命值", 20)
 *         .keyStyle(ChatFormatting.GRAY)
 *         .valueStyle(ChatFormatting.GOLD)
 *         .delimiter(" → ")
 *         .delimiterStyle(ChatFormatting.BOLD);
 * tip.add(kv.build());   // 一行显示 "生命值 → 20"，不同部分不同样式
 * </pre>
 */
public class KeyValue {
    private Component key;
    private Component value;
    private Component delimiter;
    private Style keyStyle = Style.EMPTY;
    private Style valueStyle = Style.EMPTY;
    private Style delimiterStyle = Style.EMPTY;

    /**
     * 构造键值对，key 和 value 会通过 ToolTip.parseToComponent 自动转换。
     */
    KeyValue(Object key, Object value) {
        this.key = ToolTip.parseToComponent(key);
        this.value = ToolTip.parseToComponent(value);
        this.delimiter = Component.literal(" : ");
    }

    // ---------- 样式设置（链式调用） ----------

    /**
     * 一次性设置键、分隔符、值的样式。
     * @param keyStyle 键样式（支持 ChatFormatting, Style, TextColor, 十六进制字符串, RGB整数）
     * @param delimiterStyle 分隔符样式
     * @param valueStyle 值样式
     */
    public KeyValue style(Object keyStyle, Object delimiterStyle, Object valueStyle) {
        this.keyStyle = buildStyle(keyStyle);
        this.delimiterStyle = buildStyle(delimiterStyle);
        this.valueStyle = buildStyle(valueStyle);
        return this;
    }

    public KeyValue keyStyle(Object... formats) {
        this.keyStyle = buildStyle(formats);
        return this;
    }

    public KeyValue valueStyle(Object... formats) {
        this.valueStyle = buildStyle(formats);
        return this;
    }

    public KeyValue delimiter(Component delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    public KeyValue delimiter(String text) {
        this.delimiter = Component.literal(text);
        return this;
    }

    public KeyValue delimiterStyle(Object... formats) {
        this.delimiterStyle = buildStyle(formats);
        return this;
    }

    /**
     * 构建最终的复合组件：key + delimiter + value，各自应用独立样式。
     */
    public Component build() {
        MutableComponent result = key.copy().withStyle(keyStyle);
        result.append(delimiter.copy().withStyle(delimiterStyle));
        result.append(value.copy().withStyle(valueStyle));
        return result;
    }

    /**
     * 返回未合并的列表，用于需要键值不同行的场景（例如 lines 方法）。
     */
    public List<Component> buildAsList() {
        List<Component> list = new ArrayList<>(3);
        list.add(key.copy().withStyle(keyStyle));
        list.add(delimiter.copy().withStyle(delimiterStyle));
        list.add(value.copy().withStyle(valueStyle));
        return list;
    }

    // ---------- 增强的样式解析 ----------
    /**
     * 解析一个或多个样式参数，返回合并后的 Style。
     * 支持的类型：ChatFormatting, Style, TextColor, 十六进制字符串（如 "#FFAA00"）, RGB整数。
     */
    private static Style buildStyle(Object... formats) {
        Style style = Style.EMPTY;
        for (Object obj : formats) {
            if (obj instanceof ChatFormatting fmt) {
                style = style.applyFormat(fmt);
            } else if (obj instanceof Style s) {
                style = style.applyTo(s);
            } else if (obj instanceof TextColor color) {
                style = style.withColor(color);
            } else if (obj instanceof String hex) {
                TextColor.parseColor(hex).result().ifPresent(style::withColor);
            } else if (obj instanceof Integer rgb) {
                style = style.withColor(TextColor.fromRgb(rgb));
            } // 其他类型忽略
        }
        return style;
    }
}