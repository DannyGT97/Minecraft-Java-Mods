package com.dannygt97.treechopper.client.gui;

import java.util.List;

import com.dannygt97.treechopper.tree.TreeChopperConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TreeChopperConfigScreen extends SettingsScreen {
	public TreeChopperConfigScreen(Screen parent) {
		super(parent, Component.translatable("treechopper.config.title"), "treechopper", categories(), TreeChopperConfig::resetToDefaults);
	}

	private static List<Category> categories() {
		return List.of(
			new Category(
				Component.translatable("treechopper.config.category.general"),
				Component.translatable("treechopper.config.category.general.desc"),
				List.of(
					Option.toggle("treechopper.config.enabled", TreeChopperConfig::isEnabled, TreeChopperConfig::setEnabled, () -> true),
					Option.toggle("treechopper.config.no_sneaking", TreeChopperConfig::isAllowWithoutSneaking,
						TreeChopperConfig::setAllowWithoutSneaking, TreeChopperConfig::isEnabled)
				)
			),
			new Category(
				Component.translatable("treechopper.config.category.nether"),
				Component.translatable("treechopper.config.category.nether.desc"),
				List.of(Option.toggle("treechopper.config.nether", TreeChopperConfig::isNetherFellingEnabled,
					TreeChopperConfig::setNetherFellingEnabled, TreeChopperConfig::isEnabled))
			),
			new Category(
				Component.translatable("treechopper.config.category.saplings"),
				Component.translatable("treechopper.config.category.saplings.desc"),
				List.of(Option.toggle("treechopper.config.auto_sapling", TreeChopperConfig::isAutoSaplingPlacement,
					TreeChopperConfig::setAutoSaplingPlacement, TreeChopperConfig::isEnabled))
			)
		);
	}
}
