package com.dannygt97.inventoryfilters.network;

import com.dannygt97.inventoryfilters.inventory.InventorySorter;
import com.dannygt97.inventoryfilters.inventory.InventoryTransferer;
import com.dannygt97.inventoryfilters.inventory.HotbarPresetLoader;
import com.dannygt97.inventoryfilters.inventory.BlockAutoRefillManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {
    private ModNetworking() {}

    public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(BlockAutoRefillPayload.TYPE, BlockAutoRefillPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlockAutoRefillPayload.TYPE, (payload, context) ->
			context.server().execute(() -> BlockAutoRefillManager.setEnabled(context.player(), payload.enabled()))
		);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BlockAutoRefillManager.forget(handler.player));

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

		PayloadTypeRegistry.serverboundPlay().register(LoadHotbarPresetPayload.TYPE, LoadHotbarPresetPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LoadHotbarPresetPayload.TYPE, (payload, context) ->
			context.server().execute(() -> HotbarPresetLoader.load(context.player(), payload))
		);
    }
}
