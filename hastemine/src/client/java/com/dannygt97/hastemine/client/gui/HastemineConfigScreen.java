package com.dannygt97.hastemine.client.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.dannygt97.hastemine.config.HastemineConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HastemineConfigScreen extends SettingsScreen {
	public HastemineConfigScreen(Screen parent) {
		super(parent, Component.translatable("hastemine.config.title"), "hastemine", categories(), HastemineConfig::resetToDefaults);
	}

	private static List<Category> categories() {
		// The master switch comes first; everything else is greyed out while the mod is off.
		return List.of(
			new Category(
				Component.translatable("hastemine.config.category.general"),
				Component.translatable("hastemine.config.category.general.desc"),
				List.of(toggle("enabled", HastemineConfig::isEnabled, HastemineConfig::setEnabled, () -> true))
			),
			new Category(
				Component.translatable("hastemine.config.category.deepslate"),
				Component.translatable("hastemine.config.category.deepslate.desc"),
				List.of(
					toggle("deepslate_instant", HastemineConfig::isDeepslateInstantWithHaste2,
						HastemineConfig::setDeepslateInstantWithHaste2, HastemineConfig::isEnabled),
					toggle("deepslate_stone_speed", HastemineConfig::isDeepslateAtStoneSpeed,
						HastemineConfig::setDeepslateAtStoneSpeed, HastemineConfig::isEnabled)
				)
			),
			new Category(
				Component.translatable("hastemine.config.category.cobblestone"),
				Component.translatable("hastemine.config.category.cobblestone.desc"),
				List.of(toggle("cobblestone_instant", HastemineConfig::isCobblestoneInstantWithHaste2,
					HastemineConfig::setCobblestoneInstantWithHaste2, HastemineConfig::isEnabled))
			),
			new Category(
				Component.translatable("hastemine.config.category.slabs"),
				Component.translatable("hastemine.config.category.slabs.desc"),
				List.of(toggle("slabs_instant", HastemineConfig::isSlabsInstantWithHaste2,
					HastemineConfig::setSlabsInstantWithHaste2, HastemineConfig::isEnabled))
			),
			new Category(
				Component.translatable("hastemine.config.category.logs"),
				Component.translatable("hastemine.config.category.logs.desc"),
				List.of(
					toggle("logs_instant", HastemineConfig::isLogsInstantWithHaste2,
						HastemineConfig::setLogsInstantWithHaste2, HastemineConfig::isEnabled),
					toggle("logs_haste", HastemineConfig::isLogsBedrockHaste,
						HastemineConfig::setLogsBedrockHaste, HastemineConfig::isEnabled)
				)
			)
		);
	}

	private static Option toggle(String name, BooleanSupplier getter, Consumer<Boolean> setter, BooleanSupplier active) {
		return Option.toggle("hastemine.config." + name, getter, setter, active, Component.translatable("hastemine.config." + name + ".tooltip"));
	}
}
