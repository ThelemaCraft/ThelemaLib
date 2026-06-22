package com.thelema.thelemalib.data.tool;

import net.minecraft.nbt.Tag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ClientCache {

    // 第一个String用于分类（设计类似该模块其它部分），作为 Value的 Map是高效查询的容器
    public static final Map<String, Map<String, Tag>> MAP_CACHE = new ConcurrentHashMap<>();

}