package com.zerofall.ezstorage.network;

import com.zerofall.ezstorage.EZStorage;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {

    public static final String PROTOCOL_VERSION = "1";

    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EZStorage.MOD_ID)
            .versioned(PROTOCOL_VERSION);

        // Server -> client (handlers are registered on the client, see EZStorageClient)
        registrar.playToClient(StorageContentsPayload.TYPE, StorageContentsPayload.STREAM_CODEC);
        registrar.playToClient(SelectHotbarSlotPayload.TYPE, SelectHotbarSlotPayload.STREAM_CODEC);

        // Client -> server
        registrar.playToServer(StorageClickPayload.TYPE, StorageClickPayload.STREAM_CODEC, StorageClickPayload::handle);
        registrar.playToServer(StorageDropPayload.TYPE, StorageDropPayload.STREAM_CODEC, StorageDropPayload::handle);
        registrar.playToServer(BulkImportPayload.TYPE, BulkImportPayload.STREAM_CODEC, BulkImportPayload::handle);
        registrar.playToServer(ClearGridPayload.TYPE, ClearGridPayload.STREAM_CODEC, ClearGridPayload::handle);
        registrar.playToServer(FillGridPayload.TYPE, FillGridPayload.STREAM_CODEC, FillGridPayload::handle);
        registrar.playToServer(OpenPanelPayload.TYPE, OpenPanelPayload.STREAM_CODEC, OpenPanelPayload::handle);
        registrar.playToServer(PickBlockPayload.TYPE, PickBlockPayload.STREAM_CODEC, PickBlockPayload::handle);
    }
}
