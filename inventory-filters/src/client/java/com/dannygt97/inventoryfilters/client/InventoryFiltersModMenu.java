package com.dannygt97.inventoryfilters.client;

import com.dannygt97.inventoryfilters.client.gui.InventoryFiltersConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class InventoryFiltersModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return InventoryFiltersConfigScreen::new;
	}
}
