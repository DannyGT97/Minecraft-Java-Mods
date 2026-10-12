package com.dstsoftware.dynamiclights.client;

import com.dstsoftware.dynamiclights.client.config.DynamicLightsConfig;
import com.dstsoftware.dynamiclights.client.light.DynamicLights;
import net.fabricmc.api.ClientModInitializer;

public final class DynamicLightsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		DynamicLightsConfig.load();
		DynamicLights.init();
	}
}
