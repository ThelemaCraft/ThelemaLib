package com.thelema.thelemalib.data.tool;

import net.minecraft.nbt.Tag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ClientCache {

    public static final Map<String, Tag> RAW_CACHE = new ConcurrentHashMap<>();

}