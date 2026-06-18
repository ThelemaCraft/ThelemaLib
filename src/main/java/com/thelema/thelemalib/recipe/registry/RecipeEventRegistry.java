package com.thelema.thelemalib.recipe.registry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RecipeEventRegistry {
    private static final Map<String, Consumer<PlayerEvent.ItemCraftedEvent>> REGISTRY = new HashMap<>();

    static {
        register("sound", event -> {
            ItemStack stack = event.getCrafting();
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return;
            CompoundTag tag = data.copyTag();
            if (!tag.contains("sound", CompoundTag.TAG_COMPOUND)) return;
            CompoundTag soundTag = tag.getCompound("sound");
            String soundId = soundTag.getString("sound_id");
            float volume = soundTag.getFloat("volume");
            float pitch = soundTag.getFloat("pitch");
            Player player = event.getEntity();
            if (!player.level().isClientSide) {
                ResourceLocation id = ResourceLocation.tryParse(soundId);
                if (id != null) {
                    player.level().playSound(null, player.blockPosition(),
                            SoundEvent.createVariableRangeEvent(id), SoundSource.PLAYERS, volume, pitch);
                }
            }
        });
    }

    public static void register(String tag, Consumer<PlayerEvent.ItemCraftedEvent> handler) {
        REGISTRY.put(tag, handler);
    }

    public static Consumer<PlayerEvent.ItemCraftedEvent> getHandler(String tag) {
        return REGISTRY.get(tag);
    }

    public static void init(){}
}