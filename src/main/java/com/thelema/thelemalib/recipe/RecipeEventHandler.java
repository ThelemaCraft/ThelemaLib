package com.thelema.thelemalib.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@EventBusSubscriber
public class RecipeEventHandler {

    /** 处理器映射：key = custom_data中的标记字段名，value = 接收整个ItemCraftedEvent的消费者 */
    private static final Map<String, Consumer<PlayerEvent.ItemCraftedEvent>> HANDLERS = new HashMap<>();

    /**
     * 注册一个合成后效果处理器。
     * 当玩家合成出的物品的custom_data中包含指定tagKey时，会调用此handler。
     */
    public static void register(String tagKey, Consumer<PlayerEvent.ItemCraftedEvent> handler) {
        HANDLERS.put(tagKey, handler);
    }

    public static void init(){
        register("sound", event -> {
            ItemStack stack = event.getCrafting();
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return;

            CompoundTag root = data.copyTag();
            if (!root.contains("sound", CompoundTag.TAG_COMPOUND)) return;

            CompoundTag soundTag = root.getCompound("sound");
            String soundId = soundTag.getString("sound_id");
            float volume = soundTag.getFloat("volume");
            float pitch = soundTag.getFloat("pitch");

            Player player = event.getEntity();
            if (player.level().isClientSide) return;

            ResourceLocation id = ResourceLocation.tryParse(soundId);
            if (id != null) {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvent.createVariableRangeEvent(id),
                        SoundSource.PLAYERS, volume, pitch);
            }
        });
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        CustomData data = result.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;

        CompoundTag tag = data.copyTag();
        boolean modified = false;

        // 遍历所有注册的处理器，若物品包含对应标记则执行
        for (Map.Entry<String, Consumer<PlayerEvent.ItemCraftedEvent>> entry : HANDLERS.entrySet()) {
            if (tag.contains(entry.getKey())) {
                entry.getValue().accept(event);
                tag.remove(entry.getKey());
                modified = true;
            }
        }

        // 写回修改后的 custom_data（防止重复触发）
        if (modified) {
            result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}