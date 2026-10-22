package com.dannygt97.hastemine;

import com.dannygt97.hastemine.config.HastemineConfig;
import net.fabricmc.api.ModInitializer;

public final class Hastemine implements ModInitializer {
	@Override
	public void onInitialize() {
		HastemineConfig.load();
	}
}
