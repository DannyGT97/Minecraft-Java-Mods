package com.dannygt97.hastestone.client;

import com.dannygt97.hastestone.client.gui.HastestoneConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class HastestoneModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HastestoneConfigScreen::new;
	}
}
