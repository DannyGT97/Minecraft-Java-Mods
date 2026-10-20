package io.github.dannygt97.holoprint.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.dannygt97.holoprint.ui.ConfigScreen;

/** The settings button of Mod Menu: opens the same settings window as the editor's "Cf" button. */
public final class HoloprintModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
