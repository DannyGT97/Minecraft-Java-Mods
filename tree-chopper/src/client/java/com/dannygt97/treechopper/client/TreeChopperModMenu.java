package com.dannygt97.treechopper.client;

import com.dannygt97.treechopper.client.gui.TreeChopperConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class TreeChopperModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return TreeChopperConfigScreen::new;
	}
}
