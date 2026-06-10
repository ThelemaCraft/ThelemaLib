package com.thelema.thelemalib.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelema.thelemalib.ThelemaLib;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public interface Operation {
    MapCodec<Operation> CODEC = Codec.STRING.dispatchMap("type", Operation::type, type -> {
        switch (type) {
            case "if": return If.MAP_CODEC;
            case "set_result": return SetResult.MAP_CODEC;
            case "copy": return Copy.MAP_CODEC;
            case "remove": return Remove.MAP_CODEC;
            case "modify_damage": return ModifyDamage.MAP_CODEC;
            case "modify_max_damage": return ModifyMaxDamage.MAP_CODEC;
            case "modify_count": return ModifyCount.MAP_CODEC;
            case "modify_custom": return ModifyCustom.MAP_CODEC;
            case "set_custom": return SetCustom.MAP_CODEC;
            case "modify_armor": return ModifyArmor.MAP_CODEC;
            case "modify_armor_toughness": return ModifyArmorToughness.MAP_CODEC;
            default: throw new IllegalArgumentException("Unknown operation type: " + type);
        }
    });

    String type();

    // ========== 条件接口 ==========
    interface Condition {
        MapCodec<Condition> CODEC = Codec.STRING.dispatchMap("type", Condition::type, type -> {
            switch (type) {
                case "item_id": return ItemIdCondition.MAP_CODEC;
                case "damage": return DamageCondition.MAP_CODEC;
                case "max_damage": return MaxDamageCondition.MAP_CODEC;
                case "custom_data": return CustomDataCondition.MAP_CODEC;
                case "random": return RandomCondition.MAP_CODEC;
                default: throw new IllegalArgumentException("Unknown condition type: " + type);
            }
        });
        String type();
        boolean test(ItemStack stack);
    }

    record ItemIdCondition(String item, boolean reverse) implements Condition {
        public static final MapCodec<ItemIdCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("item").forGetter(ItemIdCondition::item),
                        Codec.BOOL.optionalFieldOf("reverse", false).forGetter(ItemIdCondition::reverse)
                ).apply(inst, ItemIdCondition::new));
        @Override public String type() { return "item_id"; }
        @Override public boolean test(ItemStack stack) {
            ResourceLocation id = ResourceLocation.tryParse(item);
            if (id == null) return false;
            boolean matches = stack.is(BuiltInRegistries.ITEM.get(id));
            boolean result = reverse != matches;

            // 添加调试日志
            ThelemaLib.LOGGER.info("[TEST1 DEBUG] ItemIdCondition.test: item={}, stack={}, matches={}, reverse={}, result={}",
                    item, stack, matches, reverse, result);

            return result;
        }
    }

    record DamageCondition(String op, int value, boolean reverse) implements Condition {
        public static final MapCodec<DamageCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("op").forGetter(DamageCondition::op),
                        Codec.INT.fieldOf("value").forGetter(DamageCondition::value),
                        Codec.BOOL.optionalFieldOf("reverse", false).forGetter(DamageCondition::reverse)
                ).apply(inst, DamageCondition::new));
        @Override public String type() { return "damage"; }
        @Override public boolean test(ItemStack stack) {
            int damage = stack.getOrDefault(net.minecraft.core.component.DataComponents.DAMAGE, 0);
            boolean result = switch (op) {
                case ">" -> damage > value;
                case ">=" -> damage >= value;
                case "<" -> damage < value;
                case "<=" -> damage <= value;
                case "=" -> damage == value;
                case "!=" -> damage != value;
                default -> false;
            };
            return reverse != result;
        }
    }

    record MaxDamageCondition(String op, int value, boolean reverse) implements Condition {
        public static final MapCodec<MaxDamageCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("op").forGetter(MaxDamageCondition::op),
                        Codec.INT.fieldOf("value").forGetter(MaxDamageCondition::value),
                        Codec.BOOL.optionalFieldOf("reverse", false).forGetter(MaxDamageCondition::reverse)
                ).apply(inst, MaxDamageCondition::new));
        @Override public String type() { return "max_damage"; }
        @Override public boolean test(ItemStack stack) {
            int max = stack.getOrDefault(net.minecraft.core.component.DataComponents.MAX_DAMAGE, stack.getMaxDamage());
            boolean result = switch (op) {
                case ">" -> max > value;
                case ">=" -> max >= value;
                case "<" -> max < value;
                case "<=" -> max <= value;
                case "=" -> max == value;
                case "!=" -> max != value;
                default -> false;
            };
            return reverse != result;
        }
    }

    // 支持数字和字符串的 value
    record CustomDataCondition(String key, String op, String value, boolean reverse) implements Condition {
        public static final MapCodec<CustomDataCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("key").forGetter(CustomDataCondition::key),
                        Codec.STRING.fieldOf("op").forGetter(CustomDataCondition::op),
                        Codec.either(Codec.INT, Codec.STRING)
                                .xmap(
                                        either -> either.map(Object::toString, s -> s),
                                        s -> {
                                            try { return Either.left(Integer.parseInt(s)); }
                                            catch (NumberFormatException e) { return Either.right(s); }
                                        }
                                )
                                .fieldOf("value")
                                .forGetter(CustomDataCondition::value),
                        Codec.BOOL.optionalFieldOf("reverse", false).forGetter(CustomDataCondition::reverse)
                ).apply(inst, CustomDataCondition::new));
        @Override public String type() { return "custom_data"; }
        @Override public boolean test(ItemStack stack) {
            var customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) return reverse;
            CompoundTag tag = customData.copyTag();
            String[] keys = key.split("\\.");
            CompoundTag current = tag;
            for (int i = 0; i < keys.length - 1; i++) {
                if (!current.contains(keys[i], CompoundTag.TAG_COMPOUND)) return false;
                current = current.getCompound(keys[i]);
            }
            String last = keys[keys.length - 1];
            if (!current.contains(last)) return reverse;
            Tag t = current.get(last);
            boolean result;
            try {
                // 尝试作为数值比较
                double num = Double.parseDouble(value);
                double tagNum;
                if (t instanceof net.minecraft.nbt.NumericTag numTag) {
                    tagNum = numTag.getAsDouble();
                } else {
                    return false;
                }
                result = switch (op) {
                    case "=" -> tagNum == num;
                    case "!=" -> tagNum != num;
                    case ">" -> tagNum > num;
                    case ">=" -> tagNum >= num;
                    case "<" -> tagNum < num;
                    case "<=" -> tagNum <= num;
                    default -> false;
                };
            } catch (NumberFormatException e) {
                // 字符串比较
                String strVal = value;
                String tagStr = t.getAsString();
                result = switch (op) {
                    case "=" -> tagStr.equals(strVal);
                    case "!=" -> !tagStr.equals(strVal);
                    default -> false;
                };
            }
            return reverse != result;
        }
    }

    record RandomCondition(double chance, boolean reverse) implements Condition {
        public static final MapCodec<RandomCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.DOUBLE.fieldOf("chance").forGetter(RandomCondition::chance),
                        Codec.BOOL.optionalFieldOf("reverse", false).forGetter(RandomCondition::reverse)
                ).apply(inst, RandomCondition::new));
        @Override public String type() { return "random"; }
        @Override public boolean test(ItemStack stack) {
            boolean result = new Random().nextDouble() < chance;
            return reverse != result;
        }
    }

    // ========== 操作 ==========
    record If(List<Condition> conditions, List<Operation> then, List<Operation> elseThen) implements Operation {
        public static final MapCodec<If> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Condition.CODEC.codec().listOf().fieldOf("if").forGetter(If::conditions),
                        Operation.CODEC.codec().listOf().fieldOf("then").forGetter(If::then),
                        Operation.CODEC.codec().listOf().optionalFieldOf("else_then", List.of()).forGetter(If::elseThen)
                ).apply(inst, If::new));
        @Override public String type() { return "if"; }
    }

    record SetResult(Optional<String> itemId) implements Operation {
        public static final MapCodec<SetResult> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(Codec.STRING.optionalFieldOf("item_id").forGetter(SetResult::itemId))
                        .apply(inst, SetResult::new));
        @Override public String type() { return "set_result"; }
    }

    record Copy(boolean copyAll, List<String> keys) implements Operation {
        public static final MapCodec<Copy> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.BOOL.optionalFieldOf("copy_all", false).forGetter(Copy::copyAll),
                        Codec.STRING.listOf().optionalFieldOf("keys", List.of()).forGetter(Copy::keys)
                ).apply(inst, Copy::new));
        @Override public String type() { return "copy"; }
    }

    record Remove(List<String> keys) implements Operation {
        public static final MapCodec<Remove> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(Codec.STRING.listOf().fieldOf("keys").forGetter(Remove::keys))
                        .apply(inst, Remove::new));
        @Override public String type() { return "remove"; }
    }

    record ModifyDamage(String op, int value) implements Operation {
        public static final MapCodec<ModifyDamage> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("op").forGetter(ModifyDamage::op),
                        Codec.INT.fieldOf("value").forGetter(ModifyDamage::value)
                ).apply(inst, ModifyDamage::new));
        @Override public String type() { return "modify_damage"; }
    }

    record ModifyMaxDamage(String op, double value) implements Operation {
        public static final MapCodec<ModifyMaxDamage> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("op").forGetter(ModifyMaxDamage::op),
                        Codec.DOUBLE.fieldOf("value").forGetter(ModifyMaxDamage::value)
                ).apply(inst, ModifyMaxDamage::new));
        @Override public String type() { return "modify_max_damage"; }
    }

    record ModifyCount(String op, int value) implements Operation {
        public static final MapCodec<ModifyCount> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("op").forGetter(ModifyCount::op),
                        Codec.INT.fieldOf("value").forGetter(ModifyCount::value)
                ).apply(inst, ModifyCount::new));
        @Override public String type() { return "modify_count"; }
    }

    record ModifyCustom(String key, String op, String value) implements Operation {
        public static final MapCodec<ModifyCustom> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("key").forGetter(ModifyCustom::key),
                        Codec.STRING.fieldOf("op").forGetter(ModifyCustom::op),
                        Codec.either(Codec.DOUBLE, Codec.STRING)
                                .xmap(
                                        either -> either.map(Object::toString, s -> s),
                                        s -> {
                                            try { return Either.left(Double.parseDouble(s)); }
                                            catch (NumberFormatException e) { return Either.right(s); }
                                        }
                                )
                                .fieldOf("value")
                                .forGetter(ModifyCustom::value)
                ).apply(inst, ModifyCustom::new));
        @Override public String type() { return "modify_custom"; }
    }

    record SetCustom(String key, String value) implements Operation {
        public static final MapCodec<SetCustom> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("key").forGetter(SetCustom::key),
                        Codec.either(Codec.DOUBLE, Codec.STRING)
                                .xmap(
                                        either -> either.map(Object::toString, s -> s),
                                        s -> {
                                            try { return Either.left(Double.parseDouble(s)); }
                                            catch (NumberFormatException e) { return Either.right(s); }
                                        }
                                )
                                .fieldOf("value")
                                .forGetter(SetCustom::value)
                ).apply(inst, SetCustom::new));
        @Override public String type() { return "set_custom"; }
    }

    record ModifyArmor(String slot, String op, double value) implements Operation {
        public static final MapCodec<ModifyArmor> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("slot").forGetter(ModifyArmor::slot),
                        Codec.STRING.fieldOf("op").forGetter(ModifyArmor::op),
                        Codec.DOUBLE.fieldOf("value").forGetter(ModifyArmor::value)
                ).apply(inst, ModifyArmor::new));
        @Override public String type() { return "modify_armor"; }
    }

    record ModifyArmorToughness(String slot, String op, double value) implements Operation {
        public static final MapCodec<ModifyArmorToughness> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("slot").forGetter(ModifyArmorToughness::slot),
                        Codec.STRING.fieldOf("op").forGetter(ModifyArmorToughness::op),
                        Codec.DOUBLE.fieldOf("value").forGetter(ModifyArmorToughness::value)
                ).apply(inst, ModifyArmorToughness::new));
        @Override public String type() { return "modify_armor_toughness"; }
    }
}