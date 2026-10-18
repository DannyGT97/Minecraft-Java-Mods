package com.dannygt97.inventoryfilters.network;

import com.dannygt97.inventoryfilters.inventory.InventorySorter;
import com.dannygt97.inventoryfilters.inventory.InventoryTransferer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {
    private ModNetworking() {}

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(SortContainerPayload.TYPE, SortContainerPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SortContainerPayload.TYPE, (payload, context) ->
            context.server().execute(() ->
                InventorySorter.sort(context.player(), payload.mode(), payload.playerInventory(), payload.reverse(), payload.includeHotbar())
            )
        );

        PayloadTypeRegistry.serverboundPlay().register(ContainerTransferPayload.TYPE, ContainerTransferPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ContainerTransferPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                if (payload.stackOnly()) {
                    InventoryTransferer.autoStack(context.player(), payload.toContainer(), payload.includeHotbar());
                } else {
                    InventoryTransferer.transferAll(context.player(), payload.toContainer(), payload.includeHotbar());
                }
            })
        );
    }
}
