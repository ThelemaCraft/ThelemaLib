package com.thelema.thelemalib.tip;

import com.google.gson.JsonParser;
import com.thelema.thelemalib.config.TConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@EventBusSubscriber
public class TTManager {

    // ===== 代码注册 API（原有，保留不变） =====
    private static final Map<String, Consumer<ItemTooltipEvent>> ITEM_HANDLERS = new ConcurrentHashMap<>();
    private static final Map<TagKey<Item>, Consumer<ItemTooltipEvent>> TAG_HANDLERS = new ConcurrentHashMap<>();

    public static void item(String itemId, Consumer<ItemTooltipEvent> handler) {
        ITEM_HANDLERS.put(itemId, handler);
    }

    public static void item(DeferredHolder<Item, ? extends Item> holder, Consumer<ItemTooltipEvent> handler) {
        item(holder.getId().toString(), handler);
    }

    public static void item(Item item, Consumer<ItemTooltipEvent> handler) {
        item(BuiltInRegistries.ITEM.getKey(item).toString(), handler);
    }

    public static void tag(TagKey<Item> tag, Consumer<ItemTooltipEvent> handler) {
        TAG_HANDLERS.put(tag, handler);
    }

    public static void tag(String tagId, Consumer<ItemTooltipEvent> handler) {
        ResourceLocation loc = ResourceLocation.tryParse(tagId);
        if (loc != null) {
            tag(TagKey.create(BuiltInRegistries.ITEM.key(), loc), handler);
        }
    }

    // ===== 语言文件注入（新增） =====
    private static final Map<String, String> LANG_ITEM = new HashMap<>();
    private static final Map<String, String> LANG_TAG = new HashMap<>();
    private static final Pattern PATTERN = Pattern.compile("^tooltip\\.inject\\.(.+)$");
    private static boolean loaded = false;

    private static void loadLang() {
        if (loaded) return;
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        var path = ResourceLocation.fromNamespaceAndPath("thelemalib", "lang/" + lang + ".json");
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(path);
            if (resource.isEmpty()) return;
            try (var reader = new InputStreamReader(resource.get().open())) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                for (var e : json.entrySet()) {
                    var m = PATTERN.matcher(e.getKey());
                    if (m.matches()) {
                        String p = m.group(1);
                        // ✅ 修复：把点号换成冒号，与 BuiltInRegistries.ITEM.getKey().toString() 格式一致
                        String normalized = p.replace('.', ':');
                        if (p.startsWith("tag.")) {
                            LANG_TAG.put(p.substring(4), e.getKey());
                        } else {
                            LANG_ITEM.put(normalized, e.getKey());
                        }
                    }
                }
            }
            loaded = true;
        } catch (Exception ignored) {}
    }

    private static boolean isBlacklisted(String path) {
        return TConfig.CONFIG.tooltipBlacklist.get().contains(path);
    }

    // ===== 事件 =====
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        loadLang();
        ItemStack stack = event.getItemStack();
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // 1. 代码注册的处理器
        Consumer<ItemTooltipEvent> h = ITEM_HANDLERS.get(id);
        if (h != null) h.accept(event);
        for (var e : TAG_HANDLERS.entrySet()) {
            if (stack.is(e.getKey())) e.getValue().accept(event);
        }

        // 2. 语言文件注入（受黑名单控制）
        String key = LANG_ITEM.get(id);
        if (key != null && !isBlacklisted(id)) {
            event.getToolTip().add(Component.translatable(key));
        }
        for (var e : LANG_TAG.entrySet()) {
            if (stack.is(TagKey.create(BuiltInRegistries.ITEM.key(), ResourceLocation.parse(e.getKey())))) {
                if (!isBlacklisted("tag." + e.getKey())) {
                    event.getToolTip().add(Component.translatable(e.getValue()));
                }
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        LANG_ITEM.clear();
        LANG_TAG.clear();
        loaded = false;
    }
}