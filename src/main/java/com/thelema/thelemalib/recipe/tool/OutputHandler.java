package com.thelema.thelemalib.recipe.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.recipe.registry.HandleRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class OutputHandler {

    private static final Random RANDOM = new Random();

    /**
     * 三参数处理器接口
     */
    @FunctionalInterface
    public interface HandleConsumer {
        void accept(Context ctx, JsonObject json, MetaData meta);
    }

    /**
     * 不可变元数据：每个执行项的输入信息
     */
    public record MetaData(ItemStack input, String range, @Nullable ItemStack inputNewItem) {}

    /**
     * 执行操作列表
     */
    public static void handle(Context context, JsonArray operations) {
        for (JsonElement elem : operations) {
            JsonObject op = elem.getAsJsonObject();
            MetaData meta = initMeta(context, op);
            String type = op.get("type").getAsString();

            HandleConsumer handler = HandleRegistry.getHandle(type);
            if (handler != null) {
                handler.accept(context, op, meta);
            }else {
                ThelemaLib.LOGGER.error("OutputHandler.handle：handler == null!");
            }
        }
    }

    /**
     * 初始化元数据：从 JsonObject 中解析 input / input_new_item / range
     * 优先级：input_new_item > input > current
     */
    private static MetaData initMeta(Context ctx, JsonObject op) {
        String range = op.has("range") ? op.get("range").getAsString() : "inputs";
        ItemStack input = null;
        ItemStack inputNewItem = null;

        // 1. input_new_item 最高优先级
        if (op.has("input_new_item")) {
            JsonObject newItem = op.getAsJsonObject("input_new_item");
            ResourceLocation id = ResourceLocation.tryParse(newItem.get("id").getAsString());
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.get(id);
                int count = newItem.has("count") ? newItem.get("count").getAsInt() : 1;
                inputNewItem = new ItemStack(item, count);
                input = inputNewItem; // 直接作为本次输入
            }
        }
        // 2. input 条件搜索
        else if (op.has("input")) {
            List<ItemStack> pool = getPool(ctx, range);
            List<ItemStack> candidates = Matcher.found(pool, op.get("input"));
            if (!candidates.isEmpty()) {
                input = candidates.get(RANDOM.nextInt(candidates.size()));
            }
        }

        // 3. 默认使用 current
        if (input == null) {
            input = ctx.current;
        }

        return new MetaData(input, range, inputNewItem);
    }

    /**
     * 根据 range 获取搜索池
     */
    public static List<ItemStack> getPool(Context ctx, String range) {
        return switch (range) {
            case "outputs" -> ctx.output;
            case "both" -> {
                List<ItemStack> both = new ArrayList<>(ctx.inputs);
                both.addAll(ctx.output);
                yield both;
            }
            default -> ctx.inputs;
        };
    }
}