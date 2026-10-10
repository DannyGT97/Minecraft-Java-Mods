package com.dstsoftware.inventoryfilters.client.gui;

import com.dstsoftware.inventoryfilters.inventory.InventorySorter;
import com.dstsoftware.inventoryfilters.network.SortContainerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class SortButtonHelper {
    private static final String[] MODE_KEYS = {"type", "name", "category", "rarity"};
    private static int mode = InventorySorter.MODE_TYPE;

    private SortButtonHelper() {}

    public static Component tooltip() {
        return Component.translatable("inventoryfilters.sort.current",
            Component.translatable("inventoryfilters.sort." + MODE_KEYS[mode]));
    }

    public static void cycleAndSend(boolean playerInventory) {
        if (!ClientPlayNetworking.canSend(SortContainerPayload.TYPE)) {
            showServerRequired();
            return;
        }
        ClientPlayNetworking.send(new SortContainerPayload(mode, playerInventory));
        mode = (mode + 1) % InventorySorter.MODE_COUNT;
    }

    public static void showServerRequired() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.gui.chatListener().handleSystemMessage(Component.translatable("inventoryfilters.server_required"), false);
        }
    }
}


