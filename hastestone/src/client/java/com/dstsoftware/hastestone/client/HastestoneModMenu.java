package com.dstsoftware.hastestone.client;

import com.dstsoftware.hastestone.client.gui.HastestoneConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class HastestoneModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HastestoneConfigScreen::new;
	}
}
