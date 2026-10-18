package com.dannygt97.gameplaytweaks.client;

import com.dannygt97.gameplaytweaks.client.config.GameplayTweaksConfig;
import net.fabricmc.api.ClientModInitializer;

public final class GameplayTweaksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GameplayTweaksConfig.load();
	}
}