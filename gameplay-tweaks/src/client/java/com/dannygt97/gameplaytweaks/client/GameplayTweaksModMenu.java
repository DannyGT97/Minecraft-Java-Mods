package com.dannygt97.gameplaytweaks.client;

import com.dannygt97.gameplaytweaks.client.gui.GameplayTweaksConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class GameplayTweaksModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return GameplayTweaksConfigScreen::new;
	}
}