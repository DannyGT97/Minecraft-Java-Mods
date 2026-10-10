package com.dstsoftware.inventoryfilters.network;

import com.dstsoftware.inventoryfilters.inventory.InventorySorter;
import com.dstsoftware.inventoryfilters.inventory.InventoryTransferer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {
    private ModNetworking() {}

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(SortContainerPayload.TYPE, SortContainerPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SortContainerPayload.TYPE, (payload, context) ->
            context.server().execute(() ->
                InventorySorter.sort(context.player(), payload.mode(), payload.playerInventory())
            )
        );

        PayloadTypeRegistry.serverboundPlay().register(ContainerTransferPayload.TYPE, ContainerTransferPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ContainerTransferPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                if (payload.stackOnly()) {
                    InventoryTransferer.autoStack(context.player(), payload.toContainer());
                } else {
                    InventoryTransferer.transferAll(context.player(), payload.toContainer());
                }
            })
        );
    }
}
