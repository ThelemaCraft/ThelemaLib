package com.thelema.thelemalib.pack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber
public class TNet {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0");

        toClient(registrar);
        toServer(registrar);

    }

    private static void toServer(PayloadRegistrar registrar) {

    }

    private static void toClient(PayloadRegistrar registrar) {

    }
}