package com.thelema.thelemalib.recipe.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelema.thelemalib.recipe.tool.Context;
import com.thelema.thelemalib.recipe.tool.Matcher;
import com.thelema.thelemalib.recipe.tool.MathModifyTool;
import com.thelema.thelemalib.recipe.tool.OutputHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class HandleRegistry {
    private static final Map<String, BiConsumer<Context, JsonObject>> REGISTRY = new HashMap<>();

    static {

        register("match", (ctx, json) -> {
            // 空实现：type: match 的旧配方不会再报错，也不会执行任何逻辑
            // 所有 match 逻辑已移至通用字段处理
        });

        register("set_current", (ctx, json) -> {
            if (json.has("from")) {
                // 有 from 字段：根据条件筛选，随机选一个作为 current
                String range = json.has("range") ? json.get("range").getAsString() : "inputs";
                List<ItemStack> pool = switch (range) {
                    case "outputs" -> ctx.output;
                    case "both" -> {
                        List<ItemStack> b = new ArrayList<>(ctx.inputs);
                        b.addAll(ctx.output);
                        yield b;
                    }
                    default -> ctx.inputs;
                };
                List<ItemStack> candidates = Matcher.found(pool, json.get("from"));
                if (!candidates.isEmpty()) {
                    ctx.current = candidates.get(new Random().nextInt(candidates.size()));
                }
            }
        });

        register("set_result", (ctx, json) -> {
            // 如果有 from 字段，先根据条件设置 current
            if (json.has("from")) {
                String range = json.has("range") ? json.get("range").getAsString() : "inputs";
                List<ItemStack> pool = switch (range) {
                    case "outputs" -> ctx.output;
                    case "both" -> {
                        List<ItemStack> b = new ArrayList<>(ctx.inputs);
                        b.addAll(ctx.output);
                        yield b;
                    }
                    default -> ctx.inputs;
                };
                List<ItemStack> candidates = Matcher.found(pool, json.get("from"));
                if (!candidates.isEmpty()) {
                    // 复制，因为输出会被消耗一遍
                    ctx.current = candidates.get(new Random().nextInt(candidates.size())).copy();
                    ctx.output.set(0, ctx.current); // 同步：current 即 output[0]
                }
                return;
            }

            // 然后执行结果设置
            if (json.has("new_item_id")) {
                ResourceLocation id = ResourceLocation.tryParse(json.get("new_item_id").getAsString());
                if (id != null) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    ctx.current = new ItemStack(item);      // 更新指针
                    ctx.output.set(0, ctx.current);         // 同步输出
                    return;
                }
            }
            // 没有参数，以current为输入
            ctx.output.set(0, ctx.current);
        });

        register("add_result", (ctx, json) -> {
            // 1. 确定要添加的物品
            ItemStack toAdd;
            if (json.has("new_item_id")) {
                ResourceLocation id = ResourceLocation.tryParse(json.get("new_item_id").getAsString());
                if (id != null) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    toAdd = new ItemStack(item);
                } else {
                    return; // 无效 ID
                }
            } else if (json.has("from")) {
                String range = json.has("range") ? json.get("range").getAsString() : "inputs";
                List<ItemStack> pool = switch (range) {
                    case "outputs" -> ctx.output;
                    case "both" -> {
                        List<ItemStack> b = new ArrayList<>(ctx.inputs);
                        b.addAll(ctx.output);
                        yield b;
                    }
                    default -> ctx.inputs;
                };
                List<ItemStack> candidates = Matcher.found(pool, json.get("from"));
                if (candidates.isEmpty()) return;
                toAdd = candidates.get(new Random().nextInt(candidates.size()));
            } else {
                toAdd = ctx.current;
            }

            if (toAdd == null || toAdd.isEmpty()) return;

            // 2. 处理位置参数 pos
            int pos;
            if (json.has("pos")) {
                JsonElement posElem = json.get("pos");
                if (posElem.isJsonPrimitive() && posElem.getAsJsonPrimitive().isNumber()) {
                    pos = posElem.getAsInt();
                } else {
                    String posStr = posElem.getAsString();
                    if ("head".equalsIgnoreCase(posStr)) {
                        pos = 0;
                    } else if ("tail".equalsIgnoreCase(posStr)) {
                        pos = ctx.output.size();
                    } else {
                        pos = Integer.parseInt(posStr); // 纯数字字符串
                    }
                }
            } else {
                pos = ctx.output.size(); // 默认追加到末尾
            }

            // 边界保护：负数置0，超出则置尾
            if (pos < 0) pos = 0;
            if (pos > ctx.output.size()) pos = ctx.output.size();

            // 3. 插入物品
            ctx.output.add(pos, toAdd);
            // 4. 更新 current 指向新物品
            ctx.current = toAdd;
        });

        // === 基础修改 ===
        register("modify_damage", (ctx, json) -> {
            if (ctx.current.isEmpty()) return;
            String op = json.get("op").getAsString();
            int value = json.get("value").getAsInt();
            int old = ctx.current.getOrDefault(DataComponents.DAMAGE, 0);
            int newVal = (int) MathModifyTool.number(op, value, old);
            ctx.current.set(DataComponents.DAMAGE, Math.max(0, newVal));
        });

        register("modify_max_damage", (ctx, json) -> {
            if (ctx.current.isEmpty()) return;
            String op = json.get("op").getAsString();
            int value = json.get("value").getAsInt();
            int old = ctx.current.getOrDefault(DataComponents.MAX_DAMAGE, ctx.current.getMaxDamage());
            int newVal = (int) MathModifyTool.number(op, value, old);
            ctx.current.set(DataComponents.MAX_DAMAGE, Math.max(1, newVal));
        });

        register("modify_count", (ctx, json) -> {
            if (ctx.current.isEmpty()) return;
            String op = json.get("op").getAsString();
            int value = json.get("value").getAsInt();
            int old = ctx.current.getCount();
            int newVal = (int) MathModifyTool.number(op, value, old);
            int max = ctx.current.getItem().getMaxStackSize(ctx.current);
            ctx.current.setCount(Math.clamp(newVal, 1, max));
        });

        // === 组件复制/移除 ===
        register("copy", (ctx, json) -> {
            // 从输入中复制指定组件
            if (ctx.current.isEmpty() || ctx.inputs.isEmpty()) return;
            boolean copyAll = json.has("copy_all") && json.get("copy_all").getAsBoolean();
            ItemStack source = ctx.inputs.get(0); // 默认取第一个输入，实际可配合 match 选择
            if (copyAll) {
                ctx.current.applyComponents(source.getComponents());
            } else if (json.has("keys")) {
                JsonArray keys = json.getAsJsonArray("keys");
                for (JsonElement keyElem : keys) {
                    String key = keyElem.getAsString();
                    ResourceLocation id = ResourceLocation.tryParse(key);
                    if (id != null) {
                        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
                        if (type != null && source.has(type)) {
                            setComponent(ctx.current, type, source.get(type));
                        }
                    }
                }
            }
        });

        register("remove", (ctx, json) -> {
            if (ctx.current.isEmpty()) return;
            JsonArray keys = json.getAsJsonArray("keys");
            for (JsonElement keyElem : keys) {
                String key = keyElem.getAsString();
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) {
                    DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
                    if (type != null) {
                        ctx.current.set(type, null);
                    }
                }
            }
        });

        // === 自定义数据修改 ===
        register("modify_custom", (ctx, json) -> {
            if (ctx.current.isEmpty()) return;
            String key = json.get("key").getAsString();
            String op = json.get("op").getAsString();
            JsonElement valueElem = json.get("value");
            CustomData data = ctx.current.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            modifyNbt(tag, key, op, valueElem);
            ctx.current.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        });

        // === 属性修改 ===
        register("modify_armor", (ctx, json) -> {
            applyAttribute(ctx, Attributes.ARMOR, json);
        });

        register("modify_armor_toughness", (ctx, json) -> {
            applyAttribute(ctx, Attributes.ARMOR_TOUGHNESS, json);
        });

        register("modify_attack_speed", (ctx, json) -> {
            applyAttribute(ctx, Attributes.ATTACK_SPEED, json);
        });

        register("modify_attack_damage", (ctx, json) -> {
            applyAttribute(ctx, Attributes.ATTACK_DAMAGE, json);
        });

        register("rename", (ctx, json) -> {
            if (ctx.current.isEmpty()) return;
            String text = json.has("text") ? json.get("text").getAsString() : "";
            ctx.current.set(DataComponents.CUSTOM_NAME, Component.translatable(text));
        });

        // === 声音标记 ===
        register("sound", (ctx, json) -> {
            if (ctx.current.isEmpty()) return;
            CustomData data = ctx.current.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            CompoundTag soundTag = new CompoundTag();
            soundTag.putString("sound_id", json.get("sound_id").getAsString());
            soundTag.putFloat("volume", json.has("volume") ? json.get("volume").getAsFloat() : 1.0F);
            soundTag.putFloat("pitch", json.has("pitch") ? json.get("pitch").getAsFloat() : 1.0F);
            tag.put("sound", soundTag);
            ctx.current.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        });
    }

    // ========== 工具方法 ==========
    @SuppressWarnings("unchecked")
    private static <T> void setComponent(ItemStack stack, DataComponentType<T> type, Object value) {
        stack.set(type, (T) value);
    }

    private static void modifyNbt(CompoundTag root, String path, String op, JsonElement valueElem) {
        String[] keys = path.split("\\.");
        CompoundTag current = root;
        for (int i = 0; i < keys.length - 1; i++) {
            if (!current.contains(keys[i], CompoundTag.TAG_COMPOUND)) {
                current.put(keys[i], new CompoundTag());
            }
            current = current.getCompound(keys[i]);
        }
        String lastKey = keys[keys.length - 1];

        if (valueElem.isJsonPrimitive()) {
            var prim = valueElem.getAsJsonPrimitive();
            if (prim.isString()) {
                current.putString(lastKey, prim.getAsString());
            } else if (prim.isNumber()) {
                double newVal;
                if (op.equals("=") || op.equals("set")) {
                    newVal = prim.getAsDouble();
                } else {
                    double old = current.getDouble(lastKey);
                    newVal = MathModifyTool.number(op, prim.getAsDouble(), old);
                }
                if (prim.getAsDouble() % 1 == 0) {
                    current.putInt(lastKey, (int) newVal);
                } else {
                    current.putDouble(lastKey, newVal);
                }
            } else if (prim.isBoolean()) {
                current.putBoolean(lastKey, prim.getAsBoolean());
            }
        }
    }

    private static void applyAttribute(Context ctx, Holder<Attribute> attribute, JsonObject json) {
        if (ctx.current.isEmpty()) return;
        ItemStack original = ctx.current;
        String slot = json.has("slot") ? json.get("slot").getAsString() : "auto";
        String op = json.has("op") ? json.get("op").getAsString() : "auto";
        double value = json.get("value").getAsDouble();

        EquipmentSlotGroup slotGroup = parseSlot(slot, original);
        double oldAmount = 0.0;
        ItemAttributeModifiers currentModifiers = original.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (currentModifiers.modifiers().isEmpty() && original.getItem() instanceof ArmorItem armorItem) {
            currentModifiers = armorItem.getDefaultAttributeModifiers();
        }
        for (ItemAttributeModifiers.Entry entry : currentModifiers.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.slot().equals(slotGroup)) {
                oldAmount = entry.modifier().amount();
                break;
            }
        }
        double newAmount = MathModifyTool.number(op, value, oldAmount);

        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        boolean replaced = false;
        for (ItemAttributeModifiers.Entry entry : currentModifiers.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.slot().equals(slotGroup)) {
                AttributeModifier newMod = new AttributeModifier(entry.modifier().id(), newAmount, entry.modifier().operation());
                builder.add(attribute, newMod, slotGroup);
                replaced = true;
            } else {
                builder.add(entry.attribute(), entry.modifier(), entry.slot());
            }
        }
        if (!replaced) {
            ResourceLocation attrId = attribute.getKey().location();
            ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath("minecraft", attrId.getPath() + "." + slotGroup.getSerializedName());
            builder.add(attribute, new AttributeModifier(modifierId, newAmount, AttributeModifier.Operation.ADD_VALUE), slotGroup);
        }
        ctx.current.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }

    private static EquipmentSlotGroup parseSlot(String slot, ItemStack stack) {
        if ("auto".equals(slot) && stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlot eqSlot = armor.getEquipmentSlot();
            return switch (eqSlot) {
                case HEAD -> EquipmentSlotGroup.HEAD;
                case CHEST -> EquipmentSlotGroup.CHEST;
                case LEGS -> EquipmentSlotGroup.LEGS;
                case FEET -> EquipmentSlotGroup.FEET;
                case BODY -> EquipmentSlotGroup.BODY;
                default -> EquipmentSlotGroup.ANY;
            };
        }
        return switch (slot) {
            case "head" -> EquipmentSlotGroup.HEAD;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "feet" -> EquipmentSlotGroup.FEET;
            case "body" -> EquipmentSlotGroup.BODY;
            case "mainhand" -> EquipmentSlotGroup.MAINHAND;
            case "offhand" -> EquipmentSlotGroup.OFFHAND;
            case "hand" -> EquipmentSlotGroup.HAND;
            default -> EquipmentSlotGroup.ANY;
        };
    }

    // ========== 公共注册接口 ==========
    public static void register(String type, BiConsumer<Context, JsonObject> handler) {
        REGISTRY.put(type, handler);
    }

    public static BiConsumer<Context, JsonObject> getHandle(String type) {
        return REGISTRY.get(type);
    }

    public static void init(){}
}