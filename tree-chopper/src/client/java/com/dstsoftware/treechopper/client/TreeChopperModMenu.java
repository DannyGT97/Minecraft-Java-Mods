package com.dstsoftware.treechopper.client;

import com.dstsoftware.treechopper.client.gui.TreeChopperConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class TreeChopperModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return TreeChopperConfigScreen::new;
	}
}
