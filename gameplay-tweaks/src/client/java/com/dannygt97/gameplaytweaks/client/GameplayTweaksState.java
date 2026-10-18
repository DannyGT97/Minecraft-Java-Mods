package com.dannygt97.gameplaytweaks.client;

import com.dannygt97.gameplaytweaks.client.config.GameplayTweaksConfig;

public final class GameplayTweaksState {
	private GameplayTweaksState() {}
	public static boolean enabled() { return GameplayTweaksConfig.isBridgingEnabled(); }
}