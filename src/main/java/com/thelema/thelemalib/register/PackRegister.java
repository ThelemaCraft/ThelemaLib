package com.thelema.thelemalib.register;

import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.data.pack.PutPack;
import com.thelema.thelemalib.data.pack.RemovePack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber
public class PackRegister {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ThelemaLib.MOD_ID + "1.0");

        toClient(registrar);
        toServer(registrar);

    }

    private static void toServer(PayloadRegistrar registrar) {

    }

    private static void toClient(PayloadRegistrar registrar) {
        registrar.playToClient(
                PutPack.TYPE,
                PutPack.STREAM_CODEC,
                PutPack::handle
        );
        registrar.playToClient(
                RemovePack.TYPE,
                RemovePack.STREAM_CODEC,
                RemovePack::handle
        );
    }
}
