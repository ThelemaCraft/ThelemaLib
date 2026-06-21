package com.thelema.thelemalib.recipe;

import com.thelema.thelemalib.ThelemaLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class TRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ThelemaLib.MOD_ID);


    public static void init(IEventBus bus){
        TYPES.register(bus);
    }
}
