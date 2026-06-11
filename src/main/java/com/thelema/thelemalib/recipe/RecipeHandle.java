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

    // ---------- apply 入口 ----------
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

    // ---------- 核心流程 ----------
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
            return handleModifyDamage(damage, result);
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

    private ItemStack handleModifyDamage(Operation.ModifyDamage damage, ItemStack original) {
        int old = original.getOrDefault(DataComponents.DAMAGE, 0);
        int newVal = switch (damage.op()) {
            case "+", "add" -> old + damage.value();
            case "=", "set" -> damage.value();
            case "*", "multiply", "muti" -> old * damage.value();
            default -> {
                ThelemaLib.LOGGER.warn("Unknown op '{}' for modify_damage, keeping value", damage.op());
                yield old;
            }
        };
        ItemStack result = original.copy();
        result.set(DataComponents.DAMAGE, newVal);
        return result;
    }

    private ItemStack handleModifyMaxDamage(Operation.ModifyMaxDamage maxDamage, ItemStack original) {
        int old = original.getOrDefault(DataComponents.MAX_DAMAGE, original.getMaxDamage());
        int newVal = switch (maxDamage.op()) {
            case "+", "add" -> old + (int) maxDamage.value();
            case "=", "set" -> (int) maxDamage.value();
            case "*", "multiply", "muti" -> (int) (old * maxDamage.value());
            default -> {
                ThelemaLib.LOGGER.warn("Unknown op '{}' for modify_max_damage, keeping value", maxDamage.op());
                yield old;
            }
        };
        if (newVal < 1) newVal = 1;
        ItemStack result = original.copy();
        result.set(DataComponents.MAX_DAMAGE, newVal);
        return result;
    }

    private ItemStack handleModifyCount(Operation.ModifyCount count, ItemStack original) {
        int old = original.getCount();
        int newVal = switch (count.op()) {
            case "+", "add" -> old + count.value();
            case "=", "set" -> count.value();
            case "*", "multiply", "muti" -> old * count.value();
            default -> {
                ThelemaLib.LOGGER.warn("Unknown op '{}' for modify_count, keeping count", count.op());
                yield old;
            }
        };
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
                int newVal = switch (op) {
                    case "+", "add" -> current.getInt(last) + value.getInt();
                    case "=", "set" -> value.getInt();
                    case "*", "multiply", "muti" -> current.getInt(last) * value.getInt();
                    default -> {
                        ThelemaLib.LOGGER.warn("modify_custom: unknown op '{}' for int, keeping value", op);
                        yield current.getInt(last);
                    }
                };
                current.putInt(last, newVal);
            }
            case DOUBLE -> {
                double newVal = switch (op) {
                    case "+", "add" -> current.getDouble(last) + value.getDouble();
                    case "=", "set" -> value.getDouble();
                    case "*", "multiply", "muti" -> current.getDouble(last) * value.getDouble();
                    default -> {
                        ThelemaLib.LOGGER.warn("modify_custom: unknown op '{}' for double, keeping value", op);
                        yield current.getDouble(last);
                    }
                };
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
        EquipmentSlotGroup slot = parseSlot(armor.slot());
        return applyArmorModification(original, Attributes.ARMOR, slot, armor.op(), armor.value());
    }

    private ItemStack handleModifyArmorToughness(Operation.ModifyArmorToughness toughness, ItemStack original) {
        EquipmentSlotGroup slot = parseSlot(toughness.slot());
        return applyArmorModification(original, Attributes.ARMOR_TOUGHNESS, slot, toughness.op(), toughness.value());
    }

    private ItemStack applyArmorModification(ItemStack original, Holder<Attribute> attribute,
                                             EquipmentSlotGroup slot, String op, double value) {
        if (!(original.getItem() instanceof ArmorItem)) return original.copy();
        ItemStack result = original.copy();

        double current = getCurrentArmorValue(result, attribute, slot);
        double newValue = switch (op) {
            case "+", "add" -> current + value;
            case "=", "set" -> value;
            case "*", "multiply", "muti" -> current * value;
            default -> {
                ThelemaLib.LOGGER.warn("Unknown armor op '{}', keeping value", op);
                yield current;
            }
        };
        newValue = Math.max(0, newValue);

        ItemAttributeModifiers modifiers = result.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        var builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (!(entry.attribute().equals(attribute) && entry.slot().equals(slot))) {
                builder.add(entry.attribute(), entry.modifier(), entry.slot());
            }
        }
        // Use getKey() for safer resource location retrieval
        if (attribute.getKey() != null){
            ResourceLocation attrId = attribute.getKey().location();
            ResourceLocation modifierId = ResourceLocation.parse(ThelemaLib.MOD_ID + ":" + attrId.getPath() + "_mod");
            AttributeModifier newMod = new AttributeModifier(modifierId, newValue, AttributeModifier.Operation.ADD_VALUE);
            builder.add(attribute, newMod, slot);
            result.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        }
        return result;
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
                int val;
                try {
                    val = Integer.parseInt(modComp.value());
                } catch (NumberFormatException e) {
                    ThelemaLib.LOGGER.warn("modify_data_comp: invalid integer '{}'", modComp.value());
                    return result;
                }
                int newVal = switch (modComp.op()) {
                    case "+", "add" -> current + val;
                    case "=", "set" -> val;
                    case "*", "multiply", "muti" -> current * val;
                    default -> {
                        ThelemaLib.LOGGER.warn("modify_data_comp: unsupported op '{}' for INTEGER", modComp.op());
                        yield current;
                    }
                };
                newStack.set((DataComponentType<Integer>) compType, newVal);
            }
            case DOUBLE -> {
                double current = newStack.getOrDefault((DataComponentType<Double>) compType, 0.0);
                double val;
                try {
                    val = Double.parseDouble(modComp.value());
                } catch (NumberFormatException e) {
                    ThelemaLib.LOGGER.warn("modify_data_comp: invalid double '{}'", modComp.value());
                    return result;
                }
                double newVal = switch (modComp.op()) {
                    case "+", "add" -> current + val;
                    case "=", "set" -> val;
                    case "*", "multiply", "muti" -> current * val;
                    default -> {
                        ThelemaLib.LOGGER.warn("modify_data_comp: unsupported op '{}' for DOUBLE", modComp.op());
                        yield current;
                    }
                };
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

    // ---------- 辅助 ----------
    private EquipmentSlotGroup parseSlot(String slot) {
        return switch (slot) {
            case "head" -> EquipmentSlotGroup.HEAD;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "feet" -> EquipmentSlotGroup.FEET;
            default -> EquipmentSlotGroup.ANY;
        };
    }

    private double getCurrentArmorValue(ItemStack stack, Holder<Attribute> attribute, EquipmentSlotGroup slot) {
        double base = 0;
        if (stack.getItem() instanceof ArmorItem armorItem) {
            if (attribute == Attributes.ARMOR) {
                base = armorItem.getDefense();
            } else if (attribute == Attributes.ARMOR_TOUGHNESS) {
                base = armorItem.getToughness();
            }
        }
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double modifierSum = 0;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.slot().equals(slot)) {
                if (entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                    modifierSum += entry.modifier().amount();
                }
            }
        }
        return base + modifierSum;
    }
}