package com.dstsoftware.hastestone;

import com.dstsoftware.hastestone.config.HastestoneConfig;
import net.fabricmc.api.ModInitializer;

public final class Hastestone implements ModInitializer {
	@Override
	public void onInitialize() {
		HastestoneConfig.load();
	}
}
