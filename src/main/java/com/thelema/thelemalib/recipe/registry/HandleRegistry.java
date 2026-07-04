package com.thelema.thelemalib.recipe.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelema.thelemalib.recipe.tool.Context;
import com.thelema.thelemalib.recipe.tool.OutputHandler;
import com.thelema.thelemalib.recipe.tool.OutputHandler.HandleConsumer;
import com.thelema.thelemalib.recipe.tool.OutputHandler.MetaData;
import com.thelema.thelemalib.recipe.tool.Matcher;
import com.thelema.thelemalib.recipe.tool.MathModifyTool;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.*;

public class HandleRegistry {
    private static final Map<String, HandleConsumer> REGISTRY = new HashMap<>();

    static {
        // ========== 指针与复制 ==========

        register("set_current", (ctx, json, meta, provider) -> ctx.current = meta.input());

        register("copy_to_set_current", (ctx, json, meta, provider) -> ctx.current = meta.input().copy());

        register("set_result", (ctx, json, meta, provider) -> ctx.output.set(0, meta.input()));

        register("copy_to_set_result", (ctx, json, meta, provider) -> ctx.output.set(0, meta.input().copy()));

        register("add_result", (ctx, json, meta, provider) -> {
            // 将输入副本插入输出列表
            ItemStack copy = meta.input();
            String posStr = json.has("pos") ? json.get("pos").getAsString() : "head";
            int pos = switch (posStr) {
                case "head" -> 0;
                case "tail" -> ctx.output.size();
                default -> Integer.parseInt(posStr);
            };
            if (pos < 0) pos = 0;
            if (pos > ctx.output.size()) pos = ctx.output.size();
            ctx.output.add(pos, copy);
        });

        register("copy_to_add_result", (ctx, json, meta, provider) -> {
            // 将输入副本插入输出列表
            ItemStack copy = meta.input().copy();
            String posStr = json.has("pos") ? json.get("pos").getAsString() : "head";
            int pos = switch (posStr) {
                case "head" -> 0;
                case "tail" -> ctx.output.size();
                default -> Integer.parseInt(posStr);
            };
            if (pos < 0) pos = 0;
            if (pos > ctx.output.size()) pos = ctx.output.size();
            ctx.output.add(pos, copy);
        });

        // ========== 分支控制 ==========

        register("branch", (ctx, json, meta, provider) -> {
            if (meta.foundInput() && json.has("true")) {
                OutputHandler.handle(ctx, json.getAsJsonArray("true"), provider);
            } else if (!meta.foundInput() && json.has("false")) {
                OutputHandler.handle(ctx, json.getAsJsonArray("false"), provider);
            }
        });


        register("damage", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String op = json.get("op").getAsString();
            int value = json.get("value").getAsInt();
            int old = stack.getOrDefault(DataComponents.DAMAGE, 0);
            int newVal = (int) MathModifyTool.number(op, value, old);
            stack.set(DataComponents.DAMAGE, Math.max(0, newVal));
        });

        register("max_damage", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String op = json.get("op").getAsString();
            int value = json.get("value").getAsInt();
            int old = stack.getOrDefault(DataComponents.MAX_DAMAGE, stack.getMaxDamage());
            int newVal = (int) MathModifyTool.number(op, value, old);
            stack.set(DataComponents.MAX_DAMAGE, Math.max(1, newVal));
        });

        register("count", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String op = json.get("op").getAsString();
            int value = json.get("value").getAsInt();
            int old = stack.getCount();
            int newVal = (int) MathModifyTool.number(op, value, old);
            stack.setCount(newVal);
        });

        // ========== 属性修改 ==========

        register("armor", (ctx, json, meta, provider) -> applyAttribute(ctx, json, meta, Attributes.ARMOR));
        register("armor_toughness", (ctx, json, meta, provider) -> applyAttribute(ctx, json, meta, Attributes.ARMOR_TOUGHNESS));
        register("attack_speed", (ctx, json, meta, provider) -> applyAttribute(ctx, json, meta, Attributes.ATTACK_SPEED));
        register("attack_damage", (ctx, json, meta, provider) -> applyAttribute(ctx, json, meta, Attributes.ATTACK_DAMAGE));

        // ========== 组件操作 ==========

        register("copy_data_component", (ctx, json, meta, provider) -> {
            // source 与 target 分别解析（若未提供则使用当前 meta.input）
            ItemStack source = resolveSourceOrTarget(ctx, json, "source", meta, provider);
            ItemStack target = resolveSourceOrTarget(ctx, json, "target", meta, provider);
            if (source.isEmpty() || target.isEmpty()) return;

            boolean copyAll = json.has("copy_all") && json.get("copy_all").getAsBoolean();
            if (copyAll) {
                target.applyComponents(source.getComponents());
            } else if (json.has("key")) {
                JsonArray keys = json.getAsJsonArray("key");
                for (JsonElement keyElem : keys) {
                    String key = keyElem.getAsString();
                    ResourceLocation id = ResourceLocation.tryParse(key);
                    if (id != null) {
                        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
                        if (type != null && source.has(type)) {
                            setComponent(target, type, source.get(type));
                        }
                    }
                }
            }
        });

        register("remove_data_component", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            boolean removeAll = json.has("remove_all") && json.get("remove_all").getAsBoolean();
            if (removeAll) {
                // 危险操作：移除所有组件
                stack.remove(DataComponents.CUSTOM_NAME);
                stack.remove(DataComponents.ENCHANTMENTS);
                stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
                // ... 可根据需要扩展
            } else if (json.has("key")) {
                JsonArray keys = json.getAsJsonArray("key");
                for (JsonElement keyElem : keys) {
                    String key = keyElem.getAsString();
                    ResourceLocation id = ResourceLocation.tryParse(key);
                    if (id != null) {
                        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
                        if (type != null) {
                            stack.set(type, null);
                        }
                    }
                }
            }
        });

        // ========== 自定义 NBT ==========

        register("custom_data", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String key = json.get("key").getAsString();
            String op = json.get("op").getAsString();
            JsonElement valueElem = json.get("value");

            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            modifyNbt(tag, key, op, valueElem);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        });

        register("remove_custom_data", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            JsonElement keyElem = json.get("key");
            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            if (keyElem.isJsonArray()) {
                for (JsonElement k : keyElem.getAsJsonArray()) {
                    removeNbtPath(tag, k.getAsString());
                }
            } else {
                removeNbtPath(tag, keyElem.getAsString());
            }
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        });

        // ========== 方块实体数据(放置和破坏不丢失) ==========
        register("block_entity_data", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String key = json.get("key").getAsString();
            String op = json.get("op").getAsString();
            JsonElement valueElem = json.get("value");

            // 获取或创建 BLOCK_ENTITY_DATA
            CustomData data = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            modifyNbt(tag, key, op, valueElem);
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
        });

        register("remove_block_entity_data", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            JsonElement keyElem = json.get("key");

            CustomData data = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            if (keyElem.isJsonArray()) {
                for (JsonElement k : keyElem.getAsJsonArray()) {
                    removeNbtPath(tag, k.getAsString());
                }
            } else {
                removeNbtPath(tag, keyElem.getAsString());
            }
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
        });

        // ========== 命名 ==========

        register("rename", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String text = json.get("text").getAsString();
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(text));
        });

        // ========== 音效 ==========

        register("sound", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = data.copyTag();
            CompoundTag soundTag = new CompoundTag();
            soundTag.putString("sound_id", json.get("sound_id").getAsString());
            soundTag.putFloat("volume", json.has("volume") ? json.get("volume").getAsFloat() : 1.0F);
            soundTag.putFloat("pitch", json.has("pitch") ? json.get("pitch").getAsFloat() : 1.0F);
            tag.put("sound", soundTag);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        });

        // ========== 附魔 ==========
        register("enchant", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;

            String enchantmentId = json.get("enchantment").getAsString();
            String op = json.has("op") ? json.get("op").getAsString() : "=";
            int value = json.has("value") ? json.get("value").getAsInt() : 1;

            ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
            if (id == null) return;

            Optional<Holder.Reference<Enchantment>> holderOpt = provider.lookup(Registries.ENCHANTMENT)
                    .flatMap(lookup -> lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id)));
            if (holderOpt.isEmpty()) return;
            Holder<Enchantment> holder = holderOpt.get();

            ItemEnchantments oldEnchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            int currentLevel = oldEnchants.getLevel(holder);
            int newLevel = (int) Math.round(MathModifyTool.number(op, value, currentLevel));

            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(oldEnchants);
            mutable.set(holder, newLevel);
            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        });

        // ========== 饱食度 ==========
        register("food_nutrition", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String op = json.has("op") ? json.get("op").getAsString() : "=";
            int value = json.get("value").getAsInt();
            FoodProperties oldFood = stack.get(DataComponents.FOOD);
            if (oldFood == null) return;
            int oldVal = oldFood.nutrition();
            int newVal = (int) MathModifyTool.number(op, value, oldVal);

            FoodProperties newFood = new FoodProperties(
                    newVal,
                    oldFood.saturation(),
                    oldFood.canAlwaysEat(),
                    oldFood.eatSeconds(),
                    oldFood.usingConvertsTo(),
                    new ArrayList<>(oldFood.effects())
            );

            stack.set(DataComponents.FOOD, newFood);
        });

        // ========== 饱和 ==========
        register("food_saturation", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String op = json.has("op") ? json.get("op").getAsString() : "=";
            float value = json.get("value").getAsFloat();
            FoodProperties oldFood = stack.get(DataComponents.FOOD);
            if (oldFood == null) return;
            float oldVal = oldFood.saturation();
            float newVal = (float) MathModifyTool.number(op, value, oldVal);

            FoodProperties newFood = new FoodProperties(
                    oldFood.nutrition(),
                    newVal,
                    oldFood.canAlwaysEat(),
                    oldFood.eatSeconds(),
                    oldFood.usingConvertsTo(),
                    new ArrayList<>(oldFood.effects())
            );
            stack.set(DataComponents.FOOD, newFood);
        });

        // ========== 挖掘速度 ==========
        register("tool_speed", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            Tool oldTool = stack.get(DataComponents.TOOL);
            if (oldTool == null) return;
            String op = json.has("op") ? json.get("op").getAsString() : "=";
            float value = json.get("value").getAsFloat();
            float oldVal = oldTool.defaultMiningSpeed();
            float newVal = (float) MathModifyTool.number(op, value, oldVal);
            Tool newTool = new Tool(oldTool.rules(), newVal, oldTool.damagePerBlock());
            stack.set(DataComponents.TOOL, newTool);
        });

        // ========== 工具每方块耐久消耗 ==========
        register("damage_per_block", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            Tool oldTool = stack.get(DataComponents.TOOL);
            if (oldTool == null) return;
            String op = json.has("op") ? json.get("op").getAsString() : "=";
            float value = json.get("value").getAsFloat();
            float oldVal = oldTool.damagePerBlock();
            float newVal = (float) MathModifyTool.number(op, value, oldVal);
            // 确保非负
            newVal = Math.max(0, newVal);
            Tool newTool = new Tool(oldTool.rules(), oldTool.defaultMiningSpeed(), (int) newVal);
            stack.set(DataComponents.TOOL, newTool);
        });

        // ========== 颜色 ==========
        register("dyed_color", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;

            String op = json.has("op") ? json.get("op").getAsString() : "=";
            int value = json.get("value").getAsInt();

            DyedItemColor oldColor = stack.get(DataComponents.DYED_COLOR);
            int oldVal = oldColor != null ? oldColor.rgb() : 0xA06540;
            int newVal = (int) MathModifyTool.number(op, value, oldVal);
            newVal = Math.max(0, Math.min(0xFFFFFF, newVal));

            boolean showInTooltip = oldColor != null && oldColor.showInTooltip();
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(newVal, showInTooltip));
        });

        // ========== 药水颜色 ==========
        register("potion_color", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;

            String op = json.has("op") ? json.get("op").getAsString() : "=";
            int value = json.get("value").getAsInt();

            PotionContents oldContents = stack.get(DataComponents.POTION_CONTENTS);
            int oldVal = oldContents != null ? oldContents.customColor().orElse(0) : 0;
            int newVal = (int) MathModifyTool.number(op, value, oldVal);
            newVal = Math.max(0, Math.min(0xFFFFFF, newVal));

            PotionContents newContents;
            if (oldContents == null) {
                newContents = new PotionContents(Optional.empty(), Optional.of(newVal), List.of());
            } else {
                newContents = new PotionContents(
                        oldContents.potion(),
                        Optional.of(newVal),
                        oldContents.customEffects()
                );
            }
            stack.set(DataComponents.POTION_CONTENTS, newContents);
        });

        // ========== 旗帜基础色 ==========
        register("banner_base_color", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String op = json.has("op") ? json.get("op").getAsString() : "=";
            // 对于颜色，我们支持整数ID（0-15）或颜色名称字符串
            JsonElement valElem = json.get("value");
            DyeColor newColor = null;
            if (valElem.isJsonPrimitive()) {
                if (valElem.getAsJsonPrimitive().isNumber()) {
                    int id = valElem.getAsInt();
                    newColor = DyeColor.byId(id);
                } else if (valElem.getAsJsonPrimitive().isString()) {
                    String name = valElem.getAsString();
                    newColor = DyeColor.byName(name, null);
                }
            }
            if (newColor == null) return;
            // 直接设置 BASE_COLOR
            stack.set(DataComponents.BASE_COLOR, newColor);
        });

        // ========== 最大堆叠数 ==========
        register("max_stack_size", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String op = json.has("op") ? json.get("op").getAsString() : "=";
            int value = json.get("value").getAsInt();
            int old = stack.getOrDefault(DataComponents.MAX_STACK_SIZE, stack.getItem().getMaxStackSize(stack));
            int newVal = (int) MathModifyTool.number(op, value, old);
            // 限制 1~99（原版限制）
            newVal = Math.max(1, Math.min(99, newVal));
            stack.set(DataComponents.MAX_STACK_SIZE, newVal);
        });

        // ========== 铁砧修复花费 ==========
        register("repair_cost", (ctx, json, meta, provider) -> {
            ItemStack stack = meta.input();
            if (stack.isEmpty()) return;
            String op = json.has("op") ? json.get("op").getAsString() : "=";
            int value = json.get("value").getAsInt();
            int oldVal = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
            int newVal = (int) MathModifyTool.number(op, value, oldVal);
            // 限制最小值 0（不允许负数）
            newVal = Math.max(0, newVal);
            stack.set(DataComponents.REPAIR_COST, newVal);
        });

    }

    // ========== 公共注册接口 ==========
    public static void register(String type, HandleConsumer handler) {
        REGISTRY.put(type, handler);
    }

    public static HandleConsumer getHandle(String type) {
        return REGISTRY.get(type);
    }

    public static void init() {
    }
    // ========== 内部辅助方法 ==========

    private static void applyAttribute(Context ctx, JsonObject json, MetaData meta, Holder<Attribute> attribute) {
        ItemStack stack = meta.input();
        if (stack.isEmpty()) return;
        String slot = json.has("slot") ? json.get("slot").getAsString() : "auto";
        String op = json.has("op") ? json.get("op").getAsString() : "auto";
        double value = json.get("value").getAsDouble();

        EquipmentSlotGroup slotGroup = parseSlot(slot, stack);
        double oldAmount = 0.0;
        ItemAttributeModifiers currentModifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (currentModifiers.modifiers().isEmpty() && stack.getItem() instanceof ArmorItem armorItem) {
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
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
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

    private static ItemStack resolveSourceOrTarget(Context ctx, JsonObject json, String key, MetaData meta, HolderLookup.Provider provider) {
        if (json.has(key)) {
            // 重新初始化一次元数据（临时）
            JsonObject temp = json.getAsJsonObject(key);
            // 简单支持直接内联的条件或 input_new_item
            if (temp.has("input")) {
                List<ItemStack> pool = OutputHandler.getPool(ctx, meta.range());
                List<ItemStack> candidates = Matcher.found(pool, temp.get("input"), provider);
                if (!candidates.isEmpty()) return candidates.get(new Random().nextInt(candidates.size()));
            } else if (temp.has("input_new_item")) {
                JsonObject newItem = temp.getAsJsonObject("input_new_item");
                ResourceLocation id = ResourceLocation.tryParse(newItem.get("id").getAsString());
                if (id != null) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    int count = newItem.has("count") ? newItem.get("count").getAsInt() : 1;
                    return new ItemStack(item, count);
                }
            }
        }
        return meta.input(); // 回退到默认输入
    }

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
                if ("=".equals(op) || "set".equals(op)) {
                    current.putString(lastKey, prim.getAsString());
                } else {
                    // 不支持非等号操作字符串
                }
            } else if (prim.isNumber()) {
                double newVal;
                if ("=".equals(op) || "set".equals(op)) {
                    newVal = prim.getAsDouble();
                } else {
                    double old = current.getDouble(lastKey);
                    newVal = MathModifyTool.number(op, prim.getAsDouble(), old);
                }
                if (newVal % 1 == 0) {
                    current.putInt(lastKey, (int) newVal);
                } else {
                    current.putDouble(lastKey, newVal);
                }
            } else if (prim.isBoolean()) {
                boolean currentBool = current.getBoolean(lastKey);
                boolean newBool = "!".equals(op) ? !currentBool : prim.getAsBoolean();
                current.putBoolean(lastKey, newBool);
            }
        }
    }

    private static void removeNbtPath(CompoundTag root, String path) {
        String[] keys = path.split("\\.");
        CompoundTag current = root;
        for (int i = 0; i < keys.length - 1; i++) {
            if (!current.contains(keys[i], CompoundTag.TAG_COMPOUND)) return;
            current = current.getCompound(keys[i]);
        }
        current.remove(keys[keys.length - 1]);
    }

}