package com.dannygt97.inventoryfilters.client.gui;

import com.dannygt97.inventoryfilters.client.config.InventoryFiltersConfig;
import com.dannygt97.inventoryfilters.network.SortContainerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class SortButtonHelper {
    private SortButtonHelper() {}

    public static Component tooltip() {
        Component mode = Component.translatable("inventoryfilters.sort." + InventoryFiltersConfig.modeKey(InventoryFiltersConfig.getSortMode()));
        return Component.translatable(
            InventoryFiltersConfig.isReverseOrder() ? "inventoryfilters.sort.current_reversed" : "inventoryfilters.sort.current", mode
        );
    }

    public static void send(boolean playerInventory) {
        if (!InventoryFiltersConfig.isEnabled()) {
            return;
        }
        if (!ClientPlayNetworking.canSend(SortContainerPayload.TYPE)) {
            showServerRequired();
            return;
        }
        ClientPlayNetworking.send(new SortContainerPayload(
            InventoryFiltersConfig.getSortMode(), playerInventory, InventoryFiltersConfig.isReverseOrder(), InventoryFiltersConfig.isIncludeHotbar()
        ));
    }

    public static void showServerRequired() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.gui.chatListener().handleSystemMessage(Component.translatable("inventoryfilters.server_required"), false);
        }
    }
}
