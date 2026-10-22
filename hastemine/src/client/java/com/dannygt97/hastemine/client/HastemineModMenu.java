package com.dannygt97.hastemine.client;

import com.dannygt97.hastemine.client.gui.HastemineConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class HastemineModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HastemineConfigScreen::new;
	}
}
