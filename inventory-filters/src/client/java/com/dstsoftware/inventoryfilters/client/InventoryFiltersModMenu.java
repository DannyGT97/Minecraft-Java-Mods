package com.dstsoftware.inventoryfilters.client;

import com.dstsoftware.inventoryfilters.client.gui.InventoryFiltersConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class InventoryFiltersModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return InventoryFiltersConfigScreen::new;
	}
}
