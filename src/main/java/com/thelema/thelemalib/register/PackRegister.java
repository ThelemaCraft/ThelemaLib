package com.thelema.thelemalib.register;

import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.data.pack.S2CPutPacket;
import com.thelema.thelemalib.data.pack.S2CRemovePacket;
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
                S2CPutPacket.TYPE,
                S2CPutPacket.STREAM_CODEC,
                S2CPutPacket::handle
        );
        registrar.playToClient(
                S2CRemovePacket.TYPE,
                S2CRemovePacket.STREAM_CODEC,
                S2CRemovePacket::handle
        );
    }
}
