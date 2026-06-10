package com.thelema.thelemalib.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelema.thelemalib.ThelemaLib;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
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
import java.util.Optional;

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
        ThelemaLib.LOGGER.info("[TEST1 DEBUG] executeIf: currentOutput={}, inputs count={}", currentOutput, inputs.size());
        int foundIdx = findMatchingIndex(ifOp.conditions(), inputs);
        ThelemaLib.LOGGER.info("[TEST1 DEBUG] executeIf: foundIdx={}", foundIdx);
        if (foundIdx != -1) {
            ItemStack selected = inputs.get(foundIdx).copy();
            ThelemaLib.LOGGER.info("[TEST1 DEBUG] Executing THEN branch with selected item: {}", selected);
            return executeOperations(ifOp.then(), currentOutput, selected, inputs);
        } else if (!ifOp.elseThen().isEmpty()) {
            ThelemaLib.LOGGER.info("[TEST1 DEBUG] Executing ELSE branch");
            return executeOperations(ifOp.elseThen(), currentOutput, ItemStack.EMPTY, inputs);
        }
        ThelemaLib.LOGGER.info("[TEST1 DEBUG] No branch taken, returning currentOutput");
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
        ThelemaLib.LOGGER.info("[TEST1 DEBUG] findMatchingIndex: conditions={}, inputs={}", conditions.size(), inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack candidate = inputs.get(i);
            boolean allMatch = true;
            for (Operation.Condition cond : conditions) {
                boolean condResult = cond.test(candidate);
                ThelemaLib.LOGGER.info("[TEST1 DEBUG]   cond={} on input[{}]={} -> {}", cond.type(), i, candidate, condResult);
                if (!condResult) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                ThelemaLib.LOGGER.info("[TEST1 DEBUG] Found match at index {}", i);
                return i;
            }
        }
        ThelemaLib.LOGGER.info("[TEST1 DEBUG] No match found");
        return -1;
    }

    // ---------- 操作分发 ----------
    @SuppressWarnings("unchecked")
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
        } else if (op instanceof Operation.SetCustom setCustom) {
            return handleSetCustom(setCustom, result);
        } else if (op instanceof Operation.ModifyArmor armor) {
            return handleModifyArmor(armor, result);
        } else if (op instanceof Operation.ModifyArmorToughness toughness) {
            return handleModifyArmorToughness(toughness, result);
        } else if (op instanceof Operation.If ifOp) {
            return executeIf(ifOp, result, inputs);
        }
        return result.copy();
    }

    // ---------- 具体实现 ----------
    private ItemStack handleSetResult(Operation.SetResult setResult, ItemStack original, ItemStack current) {
        if (setResult.itemId().isPresent()) {
            ResourceLocation id = ResourceLocation.tryParse(setResult.itemId().get());
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.get(id);
                return new ItemStack(item, 1);
            }
        }
        // 无 itemId：复制当前选中的物品
        if (!current.isEmpty()) return current.copy();
        return original.copy();
    }

    private ItemStack handleCopy(Operation.Copy copy, ItemStack original, ItemStack current, List<ItemStack> inputs) {
        // 当前设计 copy 不直接指定源，只从 current 复制（因为筛选已在 if 中完成）
        // 若需要从其他输入复制，可用多个 if 分别处理
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
                // 使用 set 为 null 来真正移除，而非产生 ! 标记
                result.set((DataComponentType<Object>) type, null);
            }
        }
        return result;
    }

    private ItemStack handleModifyDamage(Operation.ModifyDamage damage, ItemStack original) {
        int old = original.getOrDefault(DataComponents.DAMAGE, 0);
        int newVal = switch (damage.op()) {
            case "add" -> old + damage.value();
            case "set" -> damage.value();
            case "multiply" -> old * damage.value();
            default -> old;
        };
        ItemStack result = original.copy();
        result.set(DataComponents.DAMAGE, newVal);
        return result;
    }

    private ItemStack handleModifyMaxDamage(Operation.ModifyMaxDamage maxDamage, ItemStack original) {
        int old = original.getOrDefault(DataComponents.MAX_DAMAGE, original.getMaxDamage());
        int newVal = switch (maxDamage.op()) {
            case "add" -> old + (int) maxDamage.value();
            case "multiply" -> (int) (old * maxDamage.value());
            case "set" -> (int) maxDamage.value();
            default -> old;
        };
        if (newVal < 1) newVal = 1;
        ItemStack result = original.copy();
        result.set(DataComponents.MAX_DAMAGE, newVal);
        return result;
    }

    private ItemStack handleModifyCount(Operation.ModifyCount count, ItemStack original) {
        int old = original.getCount();
        int newVal = switch (count.op()) {
            case "add" -> old + count.value();
            case "set" -> count.value();
            case "multiply" -> old * count.value();
            default -> old;
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
        double old = current.getDouble(last);
        double val;
        try {
            val = Double.parseDouble(modify.value());
        } catch (NumberFormatException e) {
            return result;
        }
        double newVal = switch (modify.op()) {
            case "add" -> old + val;
            case "multiply" -> old * val;
            case "set" -> val;
            default -> old;
        };
        if (newVal == (long) newVal) {
            current.putLong(last, (long) newVal);
        } else {
            current.putDouble(last, newVal);
        }
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return result;
    }

    private ItemStack handleSetCustom(Operation.SetCustom set, ItemStack original) {
        ItemStack result = original.copy();
        CustomData data = result.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();
        String[] keys = set.key().split("\\.");
        CompoundTag current = tag;
        for (int i = 0; i < keys.length - 1; i++) {
            if (!current.contains(keys[i], CompoundTag.TAG_COMPOUND)) {
                current.put(keys[i], new CompoundTag());
            }
            current = current.getCompound(keys[i]);
        }
        String last = keys[keys.length - 1];
        // 尝试解析为数字，否则作为字符串
        try {
            double num = Double.parseDouble(set.value());
            if (num == (long) num) {
                current.putLong(last, (long) num);
            } else {
                current.putDouble(last, num);
            }
        } catch (NumberFormatException e) {
            if (set.value().startsWith("\"") && set.value().endsWith("\"")) {
                current.putString(last, set.value().substring(1, set.value().length() - 1));
            } else {
                current.putString(last, set.value());
            }
        }
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return result;
    }

    private ItemStack handleModifyArmor(Operation.ModifyArmor armor, ItemStack original) {
        if (!(original.getItem() instanceof ArmorItem)) return original.copy();
        EquipmentSlotGroup slot = parseSlot(armor.slot());
        double newValue = switch (armor.op()) {
            case "add" -> armor.value();
            case "set" -> armor.value();
            default -> armor.value();
        };
        // 这里简化处理：直接修改属性修饰符，具体实现需结合原属性
        return modifyArmorAttribute(original.copy(), Attributes.ARMOR, armor.op(), newValue, slot);
    }

    private ItemStack handleModifyArmorToughness(Operation.ModifyArmorToughness toughness, ItemStack original) {
        if (!(original.getItem() instanceof ArmorItem)) return original.copy();
        EquipmentSlotGroup slot = parseSlot(toughness.slot());
        return modifyArmorAttribute(original.copy(), Attributes.ARMOR_TOUGHNESS, toughness.op(), toughness.value(), slot);
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

    private ItemStack modifyArmorAttribute(ItemStack stack, Holder<Attribute> attribute, String operation, double value, EquipmentSlotGroup slot) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().build());
        var builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }
        ResourceLocation id = ResourceLocation.parse(ThelemaLib.MOD_ID + ":" + attribute.getRegisteredName().replace(":", "_") + "_mod");
        AttributeModifier.Operation op = switch (operation) {
            case "add" -> AttributeModifier.Operation.ADD_VALUE;
            case "multiply_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "multiply_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
        AttributeModifier modifier = new AttributeModifier(id, value, op);
        builder.add(attribute, modifier, slot);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        return stack;
    }
}