package com.dstsoftware.inventoryfilters;

import com.dstsoftware.inventoryfilters.network.ModNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public final class InventoryFilters implements ModInitializer {
    public static final String MOD_ID = "inventoryfilters";

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override
    public void onInitialize() {
        ModNetworking.init();
    }
}


