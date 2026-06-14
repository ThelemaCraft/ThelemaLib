package com.thelema.thelemalib.tip;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * 工具提示多行文本构建器。
 * <p>
 * 样式方法（bold, color等）只作用于最近一次添加的组件（text/trans/add/parse），
 * 不会影响同一行中之前或之后的组件。
 */
public class ToolTip {

    private final List<Component> lines = new ArrayList<>();
    private final List<MutableComponent> currentLinePieces = new ArrayList<>(); // 当前行的独立片段
    private final List<Component> target;

    private boolean branchActive = false;
    private boolean branchCondition = true;

    // ---------- 构造器 ----------
    public ToolTip() {
        this.target = null;
    }

    public ToolTip(List<Component> target) {
        this.target = target;
    }

    // ---------- 条件分支 ----------
    public ToolTip is(boolean condition) {
        branchActive = true;
        branchCondition = condition;
        return this;
    }

    public ToolTip or() {
        if (!branchActive) return this;
        branchCondition = !branchCondition;
        return this;
    }

    private boolean shouldAdd() {
        return !branchActive || branchCondition;
    }

    private void resetBranch() {
        branchActive = false;
        branchCondition = true;
    }

    // ---------- 核心添加方法 ----------
    public ToolTip add(Component component) {
        if (shouldAdd()) {
            currentLinePieces.add(component.copy());
        }
        return this;
    }

    public ToolTip text(String text) {
        return add(Component.literal(text));
    }

    public ToolTip trans(String key) {
        return add(Component.translatable(key));
    }

    public ToolTip trans(String key, Object... args) {
        Component[] components = new Component[args.length];
        for (int i = 0; i < args.length; i++) {
            components[i] = parseToComponent(args[i]);
        }
        return add(Component.translatable(key, (Object[]) components));
    }

    public ToolTip parse(Object obj) {
        return add(parseToComponent(obj));
    }

    public ToolTip lines(List<Component> components) {
        if (shouldAdd()) {
            for (Component comp : components) {
                add(comp);
                enter();
            }
        }
        return this;
    }

    // ---------- 样式方法（作用于最后一个添加的片段） ----------
    private void applyToLastPiece(UnaryOperator<Style> modifier) {
        if (!currentLinePieces.isEmpty()) {
            MutableComponent last = currentLinePieces.get(currentLinePieces.size() - 1);
            Style newStyle = modifier.apply(last.getStyle());
            currentLinePieces.set(currentLinePieces.size() - 1, last.copy().withStyle(newStyle));
        }
    }

    public ToolTip bold() {
        applyToLastPiece(style -> style.withBold(true));
        return this;
    }

    public ToolTip italic() {
        applyToLastPiece(style -> style.withItalic(true));
        return this;
    }

    public ToolTip underline() {
        applyToLastPiece(style -> style.withUnderlined(true));
        return this;
    }

    public ToolTip strikethrough() {
        applyToLastPiece(style -> style.withStrikethrough(true));
        return this;
    }

    public ToolTip obfuscated() {
        applyToLastPiece(style -> style.withObfuscated(true));
        return this;
    }

    public ToolTip color(ChatFormatting color) {
        if (color != null && color.isColor()) {
            TextColor tc = TextColor.fromLegacyFormat(color);
            if (tc != null) {
                applyToLastPiece(style -> style.withColor(tc));
            }
        }
        return this;
    }

    public ToolTip color(String hex) {
        if (hex != null && !hex.isEmpty()) {
            TextColor.parseColor(hex).result().ifPresent(tc ->
                    applyToLastPiece(style -> style.withColor(tc))
            );
        }
        return this;
    }

    public ToolTip color(int rgb) {
        applyToLastPiece(style -> style.withColor(TextColor.fromRgb(rgb)));
        return this;
    }

    public ToolTip style(Style style) {
        if (style != null && style != Style.EMPTY) {
            applyToLastPiece(existing -> existing.applyTo(style));
        }
        return this;
    }

    public ToolTip resetStyle() {
        applyToLastPiece(style -> Style.EMPTY);
        return this;
    }

    // ---------- 换行控制 ----------
    public ToolTip enter() {
        if (!currentLinePieces.isEmpty()) {
            // 合并当前行的所有片段为一个组件
            MutableComponent line = Component.empty();
            for (MutableComponent piece : currentLinePieces) {
                line.append(piece);
            }
            lines.add(line);
            currentLinePieces.clear();
        }
        resetBranch();
        return this;
    }

    public ToolTip blank() {
        enter();
        lines.add(Component.empty());
        return this;
    }

    // ---------- 构建输出 ----------
    public List<Component> build() {
        return build(target == null ? 0 : target.size());
    }

    public List<Component> build(int index) {
        if (!currentLinePieces.isEmpty()) {
            enter();
        }
        resetBranch();
        List<Component> result = new ArrayList<>(lines);
        if (target != null) {
            target.addAll(index, result);
            return target;
        }
        return result;
    }

    // ---------- 内部辅助 ----------
    static Component parseToComponent(Object obj) {
        if (obj == null) {
            return Component.translatable("");
        }

        // 1. ResourceKey（最精确）
        if (obj instanceof ResourceKey<?> key) {
            ResourceLocation id = key.location();
            ResourceKey<? extends Registry<?>> resourceKey = key.registryKey();
            if (resourceKey == Registries.ENTITY_TYPE) {
                return Component.translatable(id.toLanguageKey("entity"));
            }
            if (resourceKey == Registries.ITEM) {
                return Component.translatable(id.toLanguageKey("item"));
            }
            if (resourceKey == Registries.BLOCK) {
                return Component.translatable(id.toLanguageKey("block"));
            }
            if (resourceKey == Registries.FLUID) {
                return Component.translatable(id.toLanguageKey("fluid"));
            }
            return Component.literal(id.toString());
        }

        // 2. ResourceLocation
        if (obj instanceof ResourceLocation loc) {
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(loc)) {
                return Component.translatable(loc.toLanguageKey("entity"));
            }
            if (BuiltInRegistries.ITEM.containsKey(loc)) {
                return Component.translatable(loc.toLanguageKey("item"));
            }
            if (BuiltInRegistries.BLOCK.containsKey(loc)) {
                return Component.translatable(loc.toLanguageKey("block"));
            }
            if (BuiltInRegistries.FLUID.containsKey(loc)) {
                return Component.translatable(loc.toLanguageKey("fluid"));
            }
            return Component.literal(loc.toString());
        }

        // 3. 其他类型
        if (obj instanceof ItemStack stack) {
            return stack.getHoverName().copy();
        }
        if (obj instanceof Item item) {
            return Component.translatable(item.getDescriptionId());
        }
        if (obj instanceof Block block) {
            return Component.translatable(block.getDescriptionId());
        }
        if (obj instanceof Fluid fluid) {
            return Component.translatable(fluid.getFluidType().getDescriptionId());
        }
        if (obj instanceof UUID uuid) {
            String shortUuid = uuid.toString().substring(0, Math.min(8, uuid.toString().length()));
            return Component.literal(shortUuid);
        }
        if (obj instanceof Component comp) {
            return comp.copy();
        }
        if (obj instanceof String str) {
            if (str.matches("^[a-z0-9_.-]+\\.[a-z0-9_.-]+$")) {
                return Component.translatable(str);
            }
            return Component.literal(str);
        }
        return Component.literal(obj.toString());
    }



}