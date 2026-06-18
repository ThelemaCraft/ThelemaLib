package com.thelema.thelemalib.recipe.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelema.thelemalib.recipe.registry.HandleRegistry;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class OutputHandler {

    public static void handle(Context context, JsonArray operations) {
        for (JsonElement elem : operations) {
            JsonObject op = elem.getAsJsonObject();

            boolean hasMatch = op.has("match");
            String matchRange = op.has("range") ? op.get("range").getAsString() : "inputs";

            if (hasMatch) {
                // 根据 matchRange 确定搜索列表
                List<ItemStack> list = switch (matchRange) {
                    case "outputs" -> context.output;
                    case "both" -> {
                        List<ItemStack> both = new ArrayList<>(context.inputs);
                        both.addAll(context.output);
                        yield both;
                    }
                    default -> context.inputs;
                };

                // 调用新的静态 match 方法，传入列表和条件元素
                boolean matched = Matcher.match(list, op.get("match"));

                if (matched && op.has("found")) {
                    handle(context, op.getAsJsonArray("found"));
                } else if (!matched && op.has("no_found")) {
                    handle(context, op.getAsJsonArray("no_found"));
                }
                // 无对应分支则什么都不做
            } else {
                String type = op.get("type").getAsString();
                BiConsumer<Context, JsonObject> handler = HandleRegistry.getHandle(type);
                if (handler != null) {
                    handler.accept(context, op);
                }
            }
        }
    }

}