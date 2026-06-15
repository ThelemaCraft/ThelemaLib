package com.thelema.thelemalib.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
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
            case "modify_armor": return ModifyArmor.MAP_CODEC;
            case "modify_armor_toughness": return ModifyArmorToughness.MAP_CODEC;
            case "modify_data_comp": return ModifyDataComp.MAP_CODEC;
            case "sound": return Sound.MAP_CODEC;
            case "modify_attack_speed": return ModifyAttackSpeed.MAP_CODEC;
            case "modify_attack_damage": return ModifyAttackDamage.MAP_CODEC;
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
                case "any_match": return AnyMatchCondition.MAP_CODEC;
                case "tag": return TagCondition.MAP_CODEC;
                default: throw new IllegalArgumentException("Unknown condition type: " + type);
            }
        });
        String type();
        boolean test(ItemStack stack);
    }

    // ========== 值类型定义 ==========
    public static final class TypedValue {
        public enum Type { STRING, INT, DOUBLE, BOOL }
        private final Type type;
        private final Object value;

        public TypedValue(Type type, Object value) {
            this.type = type;
            this.value = value;
        }
        public Type type() { return type; }
        public Object value() { return value; }
        public String getString() { return (String) value; }
        public int getInt() { return (Integer) value; }
        public double getDouble() { return (Double) value; }
        public boolean getBoolean() { return (Boolean) value; }

        public static final Codec<TypedValue> CODEC = Codec.either(
                Codec.STRING,
                Codec.either(
                        Codec.INT,
                        Codec.either(Codec.DOUBLE, Codec.BOOL)
                )
        ).xmap(
                either -> either.map(
                        str -> new TypedValue(Type.STRING, str),
                        either2 -> either2.map(
                                i -> new TypedValue(Type.INT, i),
                                either3 -> either3.map(
                                        d -> new TypedValue(Type.DOUBLE, d),
                                        b -> new TypedValue(Type.BOOL, b)
                                )
                        )
                ),
                tv -> {
                    switch (tv.type) {
                        case STRING: return Either.left(tv.getString());
                        case INT: return Either.right(Either.left(tv.getInt()));
                        case DOUBLE: return Either.right(Either.right(Either.left(tv.getDouble())));
                        case BOOL: return Either.right(Either.right(Either.right(tv.getBoolean())));
                        default: throw new IllegalStateException("Unexpected type: " + tv.type);
                    }
                }
        );
    }

    // ========== 条件实现 ==========
    record ItemIdCondition(String item, boolean reverse) implements Condition {
        public static final MapCodec<ItemIdCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("item").forGetter(ItemIdCondition::item),
                Codec.BOOL.optionalFieldOf("reverse", false).forGetter(ItemIdCondition::reverse)
        ).apply(inst, ItemIdCondition::new));
        @Override public String type() { return "item_id"; }
        @Override public boolean test(ItemStack stack) {
            ResourceLocation id = ResourceLocation.tryParse(item);
            if (id == null) return reverse;
            boolean matches = stack.is(BuiltInRegistries.ITEM.get(id));
            return reverse != matches;
        }
    }

    record DamageCondition(String op, int value, boolean reverse) implements Condition {
        public static final MapCodec<DamageCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("op").forGetter(DamageCondition::op),
                Codec.INT.fieldOf("value").forGetter(DamageCondition::value),
                Codec.BOOL.optionalFieldOf("reverse", false).forGetter(DamageCondition::reverse)
        ).apply(inst, DamageCondition::new));
        @Override public String type() { return "damage"; }
        @Override public boolean test(ItemStack stack) {
            int damage = stack.getOrDefault(DataComponents.DAMAGE, 0);
            boolean result = switch (op) {
                case ">", "greater" -> damage > value;
                case ">=", "greater_or_equal" -> damage >= value;
                case "<", "less" -> damage < value;
                case "<=", "less_or_equal" -> damage <= value;
                case "=", "==", "equals" -> damage == value;
                case "!=", "not_equals" -> damage != value;
                default -> false;
            };
            return reverse != result;
        }
    }

    record MaxDamageCondition(String op, int value, boolean reverse) implements Condition {
        public static final MapCodec<MaxDamageCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("op").forGetter(MaxDamageCondition::op),
                Codec.INT.fieldOf("value").forGetter(MaxDamageCondition::value),
                Codec.BOOL.optionalFieldOf("reverse", false).forGetter(MaxDamageCondition::reverse)
        ).apply(inst, MaxDamageCondition::new));
        @Override public String type() { return "max_damage"; }
        @Override public boolean test(ItemStack stack) {
            int max = stack.getOrDefault(DataComponents.MAX_DAMAGE, stack.getMaxDamage());
            boolean result = switch (op) {
                case ">", "greater" -> max > value;
                case ">=", "greater_or_equal" -> max >= value;
                case "<", "less" -> max < value;
                case "<=", "less_or_equal" -> max <= value;
                case "=", "==", "equals" -> max == value;
                case "!=", "not_equals" -> max != value;
                default -> false;
            };
            return reverse != result;
        }
    }

    record CustomDataCondition(String key, String op, TypedValue value, boolean reverse) implements Condition {
        public static final MapCodec<CustomDataCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("key").forGetter(CustomDataCondition::key),
                Codec.STRING.fieldOf("op").forGetter(CustomDataCondition::op),
                TypedValue.CODEC.fieldOf("value").forGetter(CustomDataCondition::value),
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
                if (!current.contains(keys[i], CompoundTag.TAG_COMPOUND)) return reverse;
                current = current.getCompound(keys[i]);
            }
            String last = keys[keys.length - 1];
            if (!current.contains(last)) return reverse;
            Tag t = current.get(last);

            boolean result;
            switch (value.type()) {
                case STRING -> {
                    if (t instanceof StringTag st) {
                        result = switch (op) {
                            case "=", "==", "equals" -> st.getAsString().equals(value.getString());
                            case "!=", "not_equals" -> !st.getAsString().equals(value.getString());
                            default -> false;
                        };
                    } else result = false;
                }
                case INT -> {
                    if (t instanceof NumericTag nt) {
                        int tagNum = nt.getAsInt();
                        result = switch (op) {
                            case "=", "==", "equals" -> tagNum == value.getInt();
                            case "!=", "not_equals" -> tagNum != value.getInt();
                            case ">", "greater" -> tagNum > value.getInt();
                            case ">=", "greater_or_equal" -> tagNum >= value.getInt();
                            case "<", "less" -> tagNum < value.getInt();
                            case "<=", "less_or_equal" -> tagNum <= value.getInt();
                            default -> false;
                        };
                    } else result = false;
                }
                case DOUBLE -> {
                    if (t instanceof NumericTag nt) {
                        double tagNum = nt.getAsDouble();
                        result = switch (op) {
                            case "=", "==", "equals" -> tagNum == value.getDouble();
                            case "!=", "not_equals" -> tagNum != value.getDouble();
                            case ">", "greater" -> tagNum > value.getDouble();
                            case ">=", "greater_or_equal" -> tagNum >= value.getDouble();
                            case "<", "less" -> tagNum < value.getDouble();
                            case "<=", "less_or_equal" -> tagNum <= value.getDouble();
                            default -> false;
                        };
                    } else result = false;
                }
                case BOOL -> {
                    if (t instanceof ByteTag bt && (bt.getAsByte() == 0 || bt.getAsByte() == 1)) {
                        boolean tagBool = bt.getAsByte() != 0;
                        result = switch (op) {
                            case "=", "==", "equals" -> tagBool == value.getBoolean();
                            case "!=", "not_equals" -> tagBool != value.getBoolean();
                            default -> false;
                        };
                    } else result = false;
                }
                default -> result = false;
            }
            return reverse != result;
        }
    }

    record RandomCondition(double chance, boolean reverse) implements Condition {
        public static final MapCodec<RandomCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.DOUBLE.fieldOf("chance").forGetter(RandomCondition::chance),
                Codec.BOOL.optionalFieldOf("reverse", false).forGetter(RandomCondition::reverse)
        ).apply(inst, RandomCondition::new));
        @Override public String type() { return "random"; }
        @Override public boolean test(ItemStack stack) {
            boolean result = new Random().nextDouble() < chance;
            return reverse != result;
        }
    }

    record AnyMatchCondition(List<Condition> conditions, boolean reverse) implements Condition {
        public static final MapCodec<AnyMatchCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Condition.CODEC.codec().listOf().fieldOf("any_match").forGetter(AnyMatchCondition::conditions),
                Codec.BOOL.optionalFieldOf("reverse", false).forGetter(AnyMatchCondition::reverse)
        ).apply(inst, AnyMatchCondition::new));
        @Override public String type() { return "any_match"; }
        @Override public boolean test(ItemStack stack) {
            for (Condition cond : conditions) {
                if (cond.test(stack)) return !reverse;
            }
            return reverse;
        }
    }

    record TagCondition(String tag, boolean reverse) implements Condition {
        public static final MapCodec<TagCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("tag").forGetter(TagCondition::tag),
                Codec.BOOL.optionalFieldOf("reverse", false).forGetter(TagCondition::reverse)
        ).apply(inst, TagCondition::new));
        @Override public String type() { return "tag"; }
        @Override public boolean test(ItemStack stack) {
            ResourceLocation location = ResourceLocation.tryParse(tag);
            if (location == null) return reverse;
            TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), location);
            boolean matches = stack.is(tagKey);
            return reverse != matches;
        }
    }

    // ========== 操作实现 ==========
    record If(List<Condition> conditions, List<Operation> then, List<Operation> elseThen) implements Operation {
        public static final MapCodec<If> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Condition.CODEC.codec().listOf().fieldOf("if").forGetter(If::conditions),
                Operation.CODEC.codec().listOf().fieldOf("then").forGetter(If::then),
                Operation.CODEC.codec().listOf().optionalFieldOf("else_then", List.of()).forGetter(If::elseThen)
        ).apply(inst, If::new));
        @Override public String type() { return "if"; }
    }

    record SetResult(Optional<String> new_item_id) implements Operation {
        public static final MapCodec<SetResult> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.optionalFieldOf("new_item_id").forGetter(SetResult::new_item_id)
        ).apply(inst, SetResult::new));
        @Override public String type() { return "set_result"; }
    }

    record Copy(boolean copyAll, List<String> keys) implements Operation {
        public static final MapCodec<Copy> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("copy_all", false).forGetter(Copy::copyAll),
                Codec.STRING.listOf().optionalFieldOf("keys", List.of()).forGetter(Copy::keys)
        ).apply(inst, Copy::new));
        @Override public String type() { return "copy"; }
    }

    record Remove(List<String> keys) implements Operation {
        public static final MapCodec<Remove> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.listOf().fieldOf("keys").forGetter(Remove::keys)
        ).apply(inst, Remove::new));
        @Override public String type() { return "remove"; }
    }

    record ModifyDamage(String op, int value) implements Operation {
        public static final MapCodec<ModifyDamage> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("op").forGetter(ModifyDamage::op),
                Codec.INT.fieldOf("value").forGetter(ModifyDamage::value)
        ).apply(inst, ModifyDamage::new));
        @Override public String type() { return "modify_damage"; }
    }

    record ModifyMaxDamage(String op, double value) implements Operation {
        public static final MapCodec<ModifyMaxDamage> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("op").forGetter(ModifyMaxDamage::op),
                Codec.DOUBLE.fieldOf("value").forGetter(ModifyMaxDamage::value)
        ).apply(inst, ModifyMaxDamage::new));
        @Override public String type() { return "modify_max_damage"; }
    }

    record ModifyCount(String op, int value) implements Operation {
        public static final MapCodec<ModifyCount> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("op").forGetter(ModifyCount::op),
                Codec.INT.fieldOf("value").forGetter(ModifyCount::value)
        ).apply(inst, ModifyCount::new));
        @Override public String type() { return "modify_count"; }
    }

    record ModifyCustom(String key, String op, TypedValue value) implements Operation {
        public static final MapCodec<ModifyCustom> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("key").forGetter(ModifyCustom::key),
                Codec.STRING.fieldOf("op").forGetter(ModifyCustom::op),
                TypedValue.CODEC.fieldOf("value").forGetter(ModifyCustom::value)
        ).apply(inst, ModifyCustom::new));
        @Override public String type() { return "modify_custom"; }
    }

    record ModifyArmor(String slot, String op, double value) implements Operation {
        public static final MapCodec<ModifyArmor> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("slot").forGetter(ModifyArmor::slot),
                Codec.STRING.optionalFieldOf("op", "auto").forGetter(ModifyArmor::op),
                Codec.DOUBLE.fieldOf("value").forGetter(ModifyArmor::value)
        ).apply(inst, ModifyArmor::new));
        @Override public String type() { return "modify_armor"; }
    }

    record ModifyArmorToughness(String slot, String op, double value) implements Operation {
        public static final MapCodec<ModifyArmorToughness> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("slot").forGetter(ModifyArmorToughness::slot),
                Codec.STRING.optionalFieldOf("op", "auto").forGetter(ModifyArmorToughness::op),
                Codec.DOUBLE.fieldOf("value").forGetter(ModifyArmorToughness::value)
        ).apply(inst, ModifyArmorToughness::new));
        @Override public String type() { return "modify_armor_toughness"; }
    }

    record ModifyDataComp(String key, String field, String op, String value) implements Operation {
        public static final MapCodec<ModifyDataComp> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("key").forGetter(ModifyDataComp::key),
                Codec.STRING.optionalFieldOf("field", "unknow").forGetter(ModifyDataComp::field),
                Codec.STRING.fieldOf("op").forGetter(ModifyDataComp::op),
                Codec.STRING.fieldOf("value").forGetter(ModifyDataComp::value)
        ).apply(inst, ModifyDataComp::new));
        @Override public String type() { return "modify_data_comp"; }
    }

    record Sound(String soundId, float volume, float pitch) implements Operation {
        public static final MapCodec<Sound> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("sound_id").forGetter(Sound::soundId),
                Codec.FLOAT.optionalFieldOf("volume", 1.0F).forGetter(Sound::volume),
                Codec.FLOAT.optionalFieldOf("pitch", 1.0F).forGetter(Sound::pitch)
        ).apply(inst, Sound::new));
        @Override public String type() { return "sound"; }
    }

    // 攻击速度修改
    record ModifyAttackSpeed(String slot, String op, double value) implements Operation {
        public static final MapCodec<ModifyAttackSpeed> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("slot").forGetter(ModifyAttackSpeed::slot),
                        Codec.STRING.fieldOf("op").forGetter(ModifyAttackSpeed::op),
                        Codec.DOUBLE.fieldOf("value").forGetter(ModifyAttackSpeed::value)
                ).apply(inst, ModifyAttackSpeed::new));
        @Override public String type() { return "modify_attack_speed"; }
    }

    // 攻击伤害修改
    record ModifyAttackDamage(String slot, String op, double value) implements Operation {
        public static final MapCodec<ModifyAttackDamage> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
                inst.group(
                        Codec.STRING.fieldOf("slot").forGetter(ModifyAttackDamage::slot),
                        Codec.STRING.fieldOf("op").forGetter(ModifyAttackDamage::op),
                        Codec.DOUBLE.fieldOf("value").forGetter(ModifyAttackDamage::value)
                ).apply(inst, ModifyAttackDamage::new));
        @Override public String type() { return "modify_attack_damage"; }
    }
}