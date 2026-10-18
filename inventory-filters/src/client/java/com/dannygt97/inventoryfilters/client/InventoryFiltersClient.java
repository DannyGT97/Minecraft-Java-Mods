package com.dannygt97.inventoryfilters.client;

import com.dannygt97.inventoryfilters.client.config.InventoryFiltersConfig;
import com.dannygt97.inventoryfilters.network.BlockAutoRefillPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class InventoryFiltersClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		InventoryFiltersConfig.load();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> syncBlockAutoRefill());
	}

	public static void syncBlockAutoRefill() {
		if (ClientPlayNetworking.canSend(BlockAutoRefillPayload.TYPE)) {
			ClientPlayNetworking.send(new BlockAutoRefillPayload(InventoryFiltersConfig.isBlockAutoRefillEnabled()));
		}
	}
}
