package com.thelema.thelemalib.recipe;

import com.mojang.serialization.Codec;
import com.thelema.thelemalib.ThelemaLib;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record RecipeHandle(List<Operation> operations) {

    public static final RecipeHandle EMPTY = new RecipeHandle(List.of());

    public static final Codec<RecipeHandle> CODEC = Codec.list(Operation.CODEC.codec())
            .xmap(RecipeHandle::new, RecipeHandle::operations);

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeHandle> STREAM_CODEC = StreamCodec.of(
            (buf, handle) -> {
                ListTag listTag = (ListTag) CODEC.encodeStart(NbtOps.INSTANCE, handle).getOrThrow();
                ByteBufCodecs.TAG.encode(buf, listTag);
            },
            buf -> {
                ListTag listTag = (ListTag) ByteBufCodecs.TAG.decode(buf);
                return CODEC.parse(NbtOps.INSTANCE, listTag).getOrThrow();
            }
    );

    // ---------- 统一算术运算 ----------
    private double applyArithmetic(double oldVal, double val, String op) {
        return switch (op) {
            case "+", "add" -> oldVal + val;
            case "=", "set" -> val;
            case "*", "multiply" -> oldVal * val;
            default -> {
                ThelemaLib.LOGGER.warn("Unknown op '{}', keeping old value", op);
                yield oldVal;
            }
        };
    }

    // ---------- 通用数值组件修改 ----------
    private ItemStack modifyIntComponent(ItemStack original, DataComponentType<Integer> comp, String op, int val) {
        int old = original.getOrDefault(comp, 0);
        int newVal = (int) applyArithmetic(old, val, op);
        ItemStack result = original.copy();
        result.set(comp, newVal);
        return result;
    }

    private ItemStack modifyDoubleComponent(ItemStack original, DataComponentType<Double> comp, String op, double val) {
        double old = original.getOrDefault(comp, 0.0);
        double newVal = applyArithmetic(old, val, op);
        ItemStack result = original.copy();
        result.set(comp, newVal);
        return result;
    }

    // ---------- 核心流程 ----------
    public ItemStack apply(ItemStack initialOutput, CraftingInput input) {
        return applyCommon(initialOutput, input.items());
    }

    public ItemStack apply(ItemStack initialOutput, SingleRecipeInput input) {
        return applyCommon(initialOutput, List.of(input.item()));
    }

    public ItemStack apply(ItemStack initialOutput, SmithingRecipeInput input) {
        return applyCommon(initialOutput, List.of(input.template(), input.base(), input.addition()));
    }

    public ItemStack apply(ItemStack initialOutput, RecipeWrapper input) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            items.add(input.getItem(i));
        }
        return applyCommon(initialOutput, items);
    }

    private ItemStack applyCommon(ItemStack initialOutput, List<ItemStack> globalInputs) {
        ItemStack finalOutput = initialOutput.copy();
        List<ItemStack> inputs = new ArrayList<>(globalInputs);

        for (Operation op : operations) {
            if (op instanceof Operation.If ifOp) {
                finalOutput = executeIf(ifOp, finalOutput, inputs);
            } else {
                finalOutput = executeTopLevelOp(op, finalOutput, inputs);
            }
        }
        return finalOutput;
    }

    private ItemStack executeIf(Operation.If ifOp, ItemStack currentOutput, List<ItemStack> inputs) {
        int foundIdx = findMatchingIndex(ifOp.conditions(), inputs);
        if (foundIdx != -1) {
            ItemStack selected = inputs.get(foundIdx).copy();
            return executeOperations(ifOp.then(), currentOutput, selected, inputs);
        } else if (!ifOp.elseThen().isEmpty()) {
            return executeOperations(ifOp.elseThen(), currentOutput, ItemStack.EMPTY, inputs);
        }
        return currentOutput;
    }

    private ItemStack executeTopLevelOp(Operation op, ItemStack currentOutput, List<ItemStack> inputs) {
        ItemStack current = inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0);
        return executeOperations(List.of(op), currentOutput, current, inputs);
    }

    private ItemStack executeOperations(List<Operation> ops, ItemStack currentOutput, ItemStack current, List<ItemStack> inputs) {
        ItemStack out = currentOutput.copy();
        for (Operation op : ops) {
            out = applyOperation(op, out, current, inputs);
        }
        return out;
    }

    private int findMatchingIndex(List<Operation.Condition> conditions, List<ItemStack> inputs) {
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack candidate = inputs.get(i);
            boolean allMatch = true;
            for (Operation.Condition cond : conditions) {
                if (!cond.test(candidate)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return i;
        }
        return -1;
    }

    // ---------- 操作分发 ----------
    private ItemStack applyOperation(Operation op, ItemStack result, ItemStack current, List<ItemStack> inputs) {
        if (op instanceof Operation.SetResult setResult) {
            return handleSetResult(setResult, result, current);
        } else if (op instanceof Operation.Copy copy) {
            return handleCopy(copy, result, current, inputs);
        } else if (op instanceof Operation.Remove remove) {
            return handleRemove(remove, result);
        } else if (op instanceof Operation.ModifyDamage damage) {
            return modifyIntComponent(result, DataComponents.DAMAGE, damage.op(), damage.value());
        } else if (op instanceof Operation.ModifyMaxDamage maxDamage) {
            return handleModifyMaxDamage(maxDamage, result);
        } else if (op instanceof Operation.ModifyCount count) {
            return handleModifyCount(count, result);
        } else if (op instanceof Operation.ModifyCustom modifyCustom) {
            return handleModifyCustom(modifyCustom, result);
        } else if (op instanceof Operation.ModifyArmor armor) {
            return handleModifyArmor(armor, result);
        } else if (op instanceof Operation.ModifyArmorToughness toughness) {
            return handleModifyArmorToughness(toughness, result);
        } else if (op instanceof Operation.If ifOp) {
            return executeIf(ifOp, result, inputs);
        } else if (op instanceof Operation.ModifyDataComp modComp) {
            return handleModifyDataComp(modComp, result);
        } else if (op instanceof Operation.Sound sound) {
            return handleSound(sound, result);
        }
        return result.copy();
    }

    // ---------- 具体实现 ----------
    private ItemStack handleSetResult(Operation.SetResult setResult, ItemStack original, ItemStack current) {
        if (setResult.itemId().isPresent()) {
            ResourceLocation id = ResourceLocation.tryParse(setResult.itemId().get());
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != Items.AIR) {
                    return new ItemStack(item, 1);
                } else {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (!current.isEmpty()) return current.copy();
        return original.copy();
    }

    @SuppressWarnings("unchecked")
    private ItemStack handleCopy(Operation.Copy copy, ItemStack original, ItemStack current, List<ItemStack> inputs) {
        ItemStack source = current.isEmpty() ? (inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0)) : current;
        if (source.isEmpty()) return original.copy();

        ItemStack result = original.copy();
        if (copy.copyAll()) {
            result.applyComponents(source.getComponents());
        } else {
            for (String key : copy.keys()) {
                String[] parts = key.split("/", 2);
                ResourceLocation id = ResourceLocation.tryParse(parts[0]);
                if (id == null) continue;
                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
                if (type != null && source.has(type)) {
                    result.set((DataComponentType<Object>) type, source.get(type));
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private ItemStack handleRemove(Operation.Remove remove, ItemStack original) {
        ItemStack result = original.copy();
        for (String key : remove.keys()) {
            String[] parts = key.split("/", 2);
            ResourceLocation id = ResourceLocation.tryParse(parts[0]);
            if (id == null) continue;
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
            if (type != null) {
                result.set((DataComponentType<Object>) type, null);
            }
        }
        return result;
    }

    private ItemStack handleModifyMaxDamage(Operation.ModifyMaxDamage maxDamage, ItemStack original) {
        int old = original.getOrDefault(DataComponents.MAX_DAMAGE, original.getMaxDamage());
        int newVal = (int) Math.max(1, applyArithmetic(old, maxDamage.value(), maxDamage.op()));
        ItemStack result = original.copy();
        result.set(DataComponents.MAX_DAMAGE, newVal);
        return result;
    }

    private ItemStack handleModifyCount(Operation.ModifyCount count, ItemStack original) {
        int old = original.getCount();
        int newVal = (int) applyArithmetic(old, count.value(), count.op());
        int maxStack = original.getItem().getMaxStackSize(original);
        newVal = Math.max(1, Math.min(newVal, maxStack));
        ItemStack result = original.copy();
        result.setCount(newVal);
        return result;
    }

    private ItemStack handleModifyCustom(Operation.ModifyCustom modify, ItemStack original) {
        ItemStack result = original.copy();
        CustomData data = result.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();
        String[] keys = modify.key().split("\\.");
        CompoundTag current = tag;
        for (int i = 0; i < keys.length - 1; i++) {
            if (!current.contains(keys[i], CompoundTag.TAG_COMPOUND)) {
                current.put(keys[i], new CompoundTag());
            }
            current = current.getCompound(keys[i]);
        }
        String last = keys[keys.length - 1];

        Operation.TypedValue value = modify.value();
        String op = modify.op();

        switch (value.type()) {
            case STRING -> {
                if (!op.equals("=") && !op.equals("set")) {
                    ThelemaLib.LOGGER.warn("modify_custom: string value only supports '=' op, got '{}'", op);
                    return result;
                }
                current.putString(last, value.getString());
            }
            case INT -> {
                int old = current.getInt(last);
                int newVal = (int) applyArithmetic(old, value.getInt(), op);
                current.putInt(last, newVal);
            }
            case DOUBLE -> {
                double old = current.getDouble(last);
                double newVal = applyArithmetic(old, value.getDouble(), op);
                current.putDouble(last, newVal);
            }
            case BOOL -> {
                if (!op.equals("=") && !op.equals("set")) {
                    ThelemaLib.LOGGER.warn("modify_custom: boolean value only supports '=' op, got '{}'", op);
                    return result;
                }
                current.putBoolean(last, value.getBoolean());
            }
        }
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return result;
    }

    private ItemStack handleModifyArmor(Operation.ModifyArmor armor, ItemStack original) {
        EquipmentSlotGroup slot = parseSlot(armor.slot(), original);
        return applyArmorModification(original, Attributes.ARMOR, slot, armor.op(), armor.value());
    }

    private ItemStack handleModifyArmorToughness(Operation.ModifyArmorToughness toughness, ItemStack original) {
        EquipmentSlotGroup slot = parseSlot(toughness.slot(), original);
        return applyArmorModification(original, Attributes.ARMOR_TOUGHNESS, slot, toughness.op(), toughness.value());
    }

    private ItemStack applyArmorModification(ItemStack original, Holder<Attribute> attribute,
                                             EquipmentSlotGroup slot, String op, double value) {
        if (!(original.getItem() instanceof ArmorItem armorItem)) return original.copy();
        ItemStack result = original.copy();

        // 强制初始化默认修饰符（避免空列表）
        ItemAttributeModifiers currentModifiers = result.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (currentModifiers == null || currentModifiers.modifiers().isEmpty()) {
            currentModifiers = armorItem.getDefaultAttributeModifiers();
            result.set(DataComponents.ATTRIBUTE_MODIFIERS, currentModifiers);
        }

        // 查找目标条目的当前 amount
        double oldAmount = 0.0;
        for (ItemAttributeModifiers.Entry entry : currentModifiers.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.slot().equals(slot)) {
                oldAmount = entry.modifier().amount();
                break;
            }
        }

        // 应用计算
        double newAmount = applyArithmetic(oldAmount, value, op);
        newAmount = Math.max(0, newAmount);

        // 构建新修饰符列表
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        boolean replaced = false;
        for (ItemAttributeModifiers.Entry entry : currentModifiers.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.slot().equals(slot)) {
                AttributeModifier newMod = new AttributeModifier(entry.modifier().id(), newAmount, entry.modifier().operation());
                builder.add(attribute, newMod, slot);
                replaced = true;
            } else {
                builder.add(entry.attribute(), entry.modifier(), entry.slot());
            }
        }
        if (!replaced) {
            ResourceLocation attrId = attribute.getKey().location();
            ResourceLocation modifierId = ResourceLocation.parse("minecraft:" + attrId.getPath() + "." + slot.getSerializedName());
            AttributeModifier newMod = new AttributeModifier(modifierId, newAmount, AttributeModifier.Operation.ADD_VALUE);
            builder.add(attribute, newMod, slot);
        }

        result.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        return result;
    }

    private ItemStack handleSound(Operation.Sound sound, ItemStack result) {
        ItemStack newStack = result.copy();
        CustomData data = newStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        CompoundTag soundTag = new CompoundTag();
        soundTag.putString("sound_id", sound.soundId());
        soundTag.putFloat("volume", sound.volume());
        soundTag.putFloat("pitch", sound.pitch());
        tag.put("sound", soundTag);
        newStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return newStack;
    }

    @SuppressWarnings("unchecked")
    private ItemStack handleModifyDataComp(Operation.ModifyDataComp modComp, ItemStack result) {
        if (result.isEmpty()) return result;

        ResourceLocation keyId = ResourceLocation.tryParse(modComp.key());
        if (keyId == null) {
            ThelemaLib.LOGGER.warn("modify_data_comp: invalid key '{}'", modComp.key());
            return result;
        }
        DataComponentType<?> compType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(keyId);
        if (compType == null) {
            ThelemaLib.LOGGER.warn("modify_data_comp: unknown component '{}'", modComp.key());
            return result;
        }

        Map<String, HandleRegistry.FieldType> fields = HandleRegistry.MANUAL_MAPPINGS.get(compType);
        if (fields == null) {
            fields = HandleRegistry.analyzeComponent(compType);
        }
        if (fields == null || !fields.containsKey(modComp.field())) {
            ThelemaLib.LOGGER.warn("modify_data_comp: no mapping for field '{}' in component '{}'", modComp.field(), modComp.key());
            return result;
        }

        HandleRegistry.FieldType fieldType = fields.get(modComp.field());
        ItemStack newStack = result.copy();

        switch (fieldType) {
            case BOOLEAN -> {
                Boolean current = newStack.getOrDefault((DataComponentType<Boolean>) compType, false);
                Boolean newVal = switch (modComp.op()) {
                    case "=", "set" -> Boolean.parseBoolean(modComp.value());
                    case "!", "toggle" -> !current;
                    default -> {
                        ThelemaLib.LOGGER.warn("modify_data_comp: op '{}' not supported for BOOLEAN", modComp.op());
                        yield current;
                    }
                };
                newStack.set((DataComponentType<Boolean>) compType, newVal);
            }
            case INTEGER -> {
                int current = newStack.getOrDefault((DataComponentType<Integer>) compType, 0);
                int val = Integer.parseInt(modComp.value());
                int newVal = (int) applyArithmetic(current, val, modComp.op());
                newStack.set((DataComponentType<Integer>) compType, newVal);
            }
            case DOUBLE -> {
                double current = newStack.getOrDefault((DataComponentType<Double>) compType, 0.0);
                double val = Double.parseDouble(modComp.value());
                double newVal = applyArithmetic(current, val, modComp.op());
                newStack.set((DataComponentType<Double>) compType, newVal);
            }
            case STRING -> {
                String current = newStack.getOrDefault((DataComponentType<String>) compType, "");
                String newVal = switch (modComp.op()) {
                    case "=", "set" -> modComp.value();
                    default -> {
                        ThelemaLib.LOGGER.warn("modify_data_comp: unsupported op '{}' for STRING", modComp.op());
                        yield current;
                    }
                };
                newStack.set((DataComponentType<String>) compType, newVal);
            }
        }
        return newStack;
    }

    // 字符串转 EquipmentSlotGroup，支持 auto
    private EquipmentSlotGroup parseSlot(String slot, ItemStack stack) {
        if ("auto".equals(slot)) {
            if (stack.getItem() instanceof ArmorItem armorItem) {
                EquipmentSlot eqSlot = armorItem.getEquipmentSlot();
                return switch (eqSlot) {
                    case HEAD -> EquipmentSlotGroup.HEAD;
                    case CHEST -> EquipmentSlotGroup.CHEST;
                    case LEGS -> EquipmentSlotGroup.LEGS;
                    case FEET -> EquipmentSlotGroup.FEET;
                    default -> EquipmentSlotGroup.ANY;
                };
            }
            return EquipmentSlotGroup.ANY;
        }
        return switch (slot) {
            case "head" -> EquipmentSlotGroup.HEAD;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "feet" -> EquipmentSlotGroup.FEET;
            default -> EquipmentSlotGroup.ANY;
        };
    }
}