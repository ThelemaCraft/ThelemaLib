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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = "your_mod_id", value = Dist.CLIENT)
public class TTManager {

    private static final Map<String, String> ITEM_CACHE = new HashMap<>();
    private static final Map<String, String> TAG_CACHE = new HashMap<>();
    private static final Pattern INJECT_PATTERN = Pattern.compile("^tooltip\\.inject\\.(.+)$");
    private static boolean loaded = false;

    // ---- 语言文件扫描 ----
    private static void loadFromLanguageFile() {
        if (loaded) return;

        String langCode = Minecraft.getInstance().getLanguageManager().getSelected();
        ResourceLocation langPath = ResourceLocation.fromNamespaceAndPath(
                "your_mod_id",
                "lang/" + langCode + ".json"
        );

        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(langPath);
            if (resource.isEmpty()) return;

            try (var reader = new InputStreamReader(resource.get().open())) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();

                for (var entry : json.entrySet()) {
                    String key = entry.getKey();
                    var matcher = INJECT_PATTERN.matcher(key);
                    if (matcher.matches()) {
                        String path = matcher.group(1); // 如 "minecraft.diamond" 或 "tag.minecraft.logs"
                        if (path.startsWith("tag.")) {
                            String tagId = path.substring(4);
                            TAG_CACHE.put(tagId, key);
                        } else {
                            ITEM_CACHE.put(path, key);
                        }
                    }
                }
            }
            loaded = true;
        } catch (Exception e) {
            // 忽略加载失败（例如语言文件不存在）
        }
    }

    // ---- 黑名单检查 ----
    private static boolean isBlacklisted(String path) {
        List<? extends String> blacklist = TConfig.CONFIG.tooltipBlacklist.get();
        return blacklist.contains(path);
    }

    // ---- 事件处理 ----
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        loadFromLanguageFile();

        ItemStack stack = event.getItemStack();
        var tooltip = event.getToolTip();

        // 1. 精确匹配物品 ID
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String translationKey = ITEM_CACHE.get(itemId);
        if (translationKey != null) {
            // 检查黑名单（路径就是物品 ID）
            if (!isBlacklisted(itemId)) {
                tooltip.add(Component.translatable(translationKey));
            }
        }

        // 2. 匹配标签
        for (Map.Entry<String, String> entry : TAG_CACHE.entrySet()) {
            String tagId = entry.getKey();
            TagKey<Item> tagKey = TagKey.create(
                    BuiltInRegistries.ITEM.key(),
                    ResourceLocation.parse(tagId)
            );
            if (stack.is(tagKey)) {
                // 检查黑名单（路径格式 "tag." + tagId）
                String path = "tag." + tagId;
                if (!isBlacklisted(path)) {
                    tooltip.add(Component.translatable(entry.getValue()));
                }
                break; // 只添加第一个匹配的标签
            }
        }
    }

    // ---- 资源重载（F3+T）清空缓存 ----
    @SubscribeEvent
    public static void onResourceReload(AddReloadListenerEvent event) {
        ITEM_CACHE.clear();
        TAG_CACHE.clear();
        loaded = false;
    }
}