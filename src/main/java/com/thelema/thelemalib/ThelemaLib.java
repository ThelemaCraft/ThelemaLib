package com.thelema.thelemalib;

import com.thelema.thelemalib.config.TConfig;
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
        
        bus.addListener(this::commonSetup);


        cont.registerConfig(ModConfig.Type.COMMON, TConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }


}
