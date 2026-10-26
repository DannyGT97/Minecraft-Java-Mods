package com.dannygt97.gameplaytweaks.client.gui;

import java.util.List;

import com.dannygt97.gameplaytweaks.client.config.GameplayTweaksConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

public final class GameplayTweaksConfigScreen extends SettingsScreen {
	public GameplayTweaksConfigScreen(Screen parent) {
		super(parent, Component.translatable("gameplaytweaks.config.title"), "gameplaytweaks", List.of(
			new Category(
				Component.translatable("gameplaytweaks.config.category.fog"),
				Component.translatable("gameplaytweaks.config.category.fog.desc"),
				List.of(
					Option.toggle("gameplaytweaks.config.fog.overworld", () -> !GameplayTweaksConfig.isFogDisabled("overworld"),
						value -> GameplayTweaksConfig.setFogDisabled("overworld", !value), () -> true),
					Option.toggle("gameplaytweaks.config.fog.nether", () -> !GameplayTweaksConfig.isFogDisabled("nether"),
						value -> GameplayTweaksConfig.setFogDisabled("nether", !value), () -> true),
					Option.toggle("gameplaytweaks.config.fog.end", () -> !GameplayTweaksConfig.isFogDisabled("end"),
						value -> GameplayTweaksConfig.setFogDisabled("end", !value), () -> true)
				)
			),
			new Category(
				Component.translatable("gameplaytweaks.config.category.lighting"),
				Component.translatable("gameplaytweaks.config.category.lighting.desc"),
				List.of(
					Option.toggle("gameplaytweaks.config.fullbright", GameplayTweaksConfig::isFullbrightEnabled,
						GameplayTweaksConfig::setFullbrightEnabled, () -> true),
					Option.toggle("gameplaytweaks.config.light_mesh", GameplayTweaksConfig::isLightMeshEnabled,
						GameplayTweaksConfig::setLightMeshEnabled, () -> true),
					Option.cycle("gameplaytweaks.config.light_mesh_opacity", GameplayTweaksConfig::getLightMeshOpacity,
						GameplayTweaksConfig::setLightMeshOpacity, new int[] {10, 25, 50, 75, 100}, GameplayTweaksConfig::isLightMeshEnabled))
			),
			new Category(
				Component.translatable("gameplaytweaks.config.category.gameplay"),
				Component.translatable("gameplaytweaks.config.category.gameplay.desc"),
				List.of(Option.toggle("gameplaytweaks.config.bridging", GameplayTweaksConfig::isBridgingEnabled,
					GameplayTweaksConfig::setBridgingEnabled, () -> true))
			)
		), () -> {
			GameplayTweaksConfig.setFogDisabled("overworld", false);
			GameplayTweaksConfig.setFogDisabled("nether", false);
			GameplayTweaksConfig.setFogDisabled("end", false);
			GameplayTweaksConfig.setFullbrightEnabled(false);
			GameplayTweaksConfig.setBridgingEnabled(false);
			GameplayTweaksConfig.setLightMeshEnabled(false);
			GameplayTweaksConfig.setLightMeshOpacity(100);
		});
	}
}
