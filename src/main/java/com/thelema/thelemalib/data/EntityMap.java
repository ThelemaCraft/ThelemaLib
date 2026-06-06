package com.thelema.thelemalib.data;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

public class EntityMap {

    public static final String DEFAULT = "default_entity_map";

    private Entity entity;
    private RegistryAccess provider;
    private String key;
    @SuppressWarnings("rawtypes")
    public Map map;

    @SuppressWarnings("resource")
    public static EntityMap get(Entity entity, String key){
        EntityMap em = new EntityMap();
        CompoundTag data = entity.getPersistentData();

        em.provider = entity.level().registryAccess();

        if (data.contains(key, Tag.TAG_COMPOUND)){
            // 有，获取 NBT 变为 Map
            em.map = MapHelper.fromNbt(data.getCompound(key), em.provider);
        }else {
            // 没有，空 map
            em.map = new HashMap<>();
        }
        return em;
    }

    public static EntityMap get(Entity entity){
        return get(entity, DEFAULT);
    }

    public void save(){
        // Map 变回 NBT
        CompoundTag nbt = MapHelper.toNBT(map, provider);
        // 放
        entity.getPersistentData().put(key, nbt);
    }

}
