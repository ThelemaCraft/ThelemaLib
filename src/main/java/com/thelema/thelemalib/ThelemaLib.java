package com.thelema.thelemalib;

import com.thelema.thelemalib.area.AreaRegistry;
import com.thelema.thelemalib.config.TConfig;
import com.thelema.thelemalib.data.registry.KeyRegistry;
import com.thelema.thelemalib.data.registry.ValueRegistry;
import com.thelema.thelemalib.register.ItemRegister;
import com.thelema.thelemalib.recipe.TRecipeSerializers;
import com.thelema.thelemalib.recipe.TRecipeTypes;
import com.thelema.thelemalib.recipe.registry.ConditionRegistry;
import com.thelema.thelemalib.recipe.registry.HandleRegistry;
import com.thelema.thelemalib.recipe.registry.RecipeEventRegistry;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(ThelemaLib.MOD_ID)
public class ThelemaLib {

    public static final String MOD_ID = "thelemalib";

    public static final Logger LOGGER = LogUtils.getLogger();


    public ThelemaLib(IEventBus bus, ModContainer cont) {

        ItemRegister.register(bus);

        // recipe 包初始化
        TRecipeSerializers.init(bus);
        TRecipeTypes.init(bus);

        // kv 包初始化
        ConditionRegistry.init();
        HandleRegistry.init();
        RecipeEventRegistry.init();
        KeyRegistry.init();
        ValueRegistry.init();
        AreaRegistry.init();

        cont.registerConfig(ModConfig.Type.COMMON, TConfig.SPEC);
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }


}
