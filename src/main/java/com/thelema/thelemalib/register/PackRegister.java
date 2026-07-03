package com.thelema.thelemalib.register;

import com.thelema.thelemalib.ThelemaLib;
import com.thelema.thelemalib.data.tool.MapDeltaPutSyncPack;
import com.thelema.thelemalib.data.tool.MapDeltaRemoveSyncPack;
import com.thelema.thelemalib.data.tool.MapSyncPack;
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
        registrar.playToClient(MapSyncPack.TYPE, MapSyncPack.STREAM_CODEC, MapSyncPack::handle);
        registrar.playToClient(MapDeltaPutSyncPack.TYPE, MapDeltaPutSyncPack.STREAM_CODEC, MapDeltaPutSyncPack::handle);
        registrar.playToClient(MapDeltaRemoveSyncPack.TYPE, MapDeltaRemoveSyncPack.STREAM_CODEC, MapDeltaRemoveSyncPack::handle);
    }
}
