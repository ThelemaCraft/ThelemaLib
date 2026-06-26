package com.thelema.thelemalib.data.tool;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

public interface Adapt<T> {
    Tag toTag(T obj, HolderLookup.Provider provider);
    T fromTag(Tag tag, HolderLookup.Provider provider);
}