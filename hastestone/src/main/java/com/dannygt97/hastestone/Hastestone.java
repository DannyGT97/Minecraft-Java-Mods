package com.dannygt97.hastestone;

import com.dannygt97.hastestone.config.HastestoneConfig;
import net.fabricmc.api.ModInitializer;

public final class Hastestone implements ModInitializer {
	@Override
	public void onInitialize() {
		HastestoneConfig.load();
	}
}
