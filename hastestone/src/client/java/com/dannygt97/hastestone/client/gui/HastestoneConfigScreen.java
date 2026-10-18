package com.dannygt97.hastestone.client.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.dannygt97.hastestone.config.HastestoneConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HastestoneConfigScreen extends SettingsScreen {
	public HastestoneConfigScreen(Screen parent) {
		super(parent, Component.translatable("hastestone.config.title"), "hastestone", categories(), HastestoneConfig::resetToDefaults);
	}

	private static List<Category> categories() {
		// The master switch comes first; everything else is greyed out while the mod is off.
		return List.of(
			new Category(
				Component.translatable("hastestone.config.category.general"),
				Component.translatable("hastestone.config.category.general.desc"),
				List.of(toggle("enabled", HastestoneConfig::isEnabled, HastestoneConfig::setEnabled, () -> true))
			),
			new Category(
				Component.translatable("hastestone.config.category.deepslate"),
				Component.translatable("hastestone.config.category.deepslate.desc"),
				List.of(
					toggle("deepslate_instant", HastestoneConfig::isDeepslateInstantWithHaste2,
						HastestoneConfig::setDeepslateInstantWithHaste2, HastestoneConfig::isEnabled),
					toggle("deepslate_stone_speed", HastestoneConfig::isDeepslateAtStoneSpeed,
						HastestoneConfig::setDeepslateAtStoneSpeed, HastestoneConfig::isEnabled)
				)
			),
			new Category(
				Component.translatable("hastestone.config.category.cobblestone"),
				Component.translatable("hastestone.config.category.cobblestone.desc"),
				List.of(toggle("cobblestone_instant", HastestoneConfig::isCobblestoneInstantWithHaste2,
					HastestoneConfig::setCobblestoneInstantWithHaste2, HastestoneConfig::isEnabled))
			)
		);
	}

	private static Option toggle(String name, BooleanSupplier getter, Consumer<Boolean> setter, BooleanSupplier active) {
		return Option.toggle("hastestone.config." + name, getter, setter, active, Component.translatable("hastestone.config." + name + ".tooltip"));
	}
}
