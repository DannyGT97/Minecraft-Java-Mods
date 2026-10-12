package com.dstsoftware.dynamiclights.client;

import com.dstsoftware.dynamiclights.client.gui.DynamicLightsConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class DynamicLightsModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return DynamicLightsConfigScreen::new;
	}
}
