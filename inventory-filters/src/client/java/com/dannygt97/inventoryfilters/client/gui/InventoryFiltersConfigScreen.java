package com.dannygt97.inventoryfilters.client.gui;

import java.util.List;

import com.dannygt97.inventoryfilters.client.config.InventoryFiltersConfig;
import com.dannygt97.inventoryfilters.inventory.InventorySorter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class InventoryFiltersConfigScreen extends SettingsScreen {
	public static final int HOTBAR_PRESETS_CATEGORY = 4;

	public InventoryFiltersConfigScreen(Screen parent) {
		this(parent, 0);
	}

	public InventoryFiltersConfigScreen(Screen parent, int initialCategory) {
		super(parent, Component.translatable("inventoryfilters.config.title"), "inventoryfilters", categories(), InventoryFiltersConfig::resetToDefaults, initialCategory);
	}

	private static List<Category> categories() {
		return List.of(
			// The master switch comes first; everything else is greyed out while the mod is off.
			new Category(
				Component.translatable("inventoryfilters.config.category.general"),
				Component.translatable("inventoryfilters.config.category.general.desc"),
				List.of(Option.toggle("inventoryfilters.config.enabled", InventoryFiltersConfig::isEnabled, InventoryFiltersConfig::setEnabled, () -> true))
			),
			new Category(
				Component.translatable("inventoryfilters.config.category.sorting"),
				Component.translatable("inventoryfilters.config.category.sorting.desc"),
				List.of(
					new Option(
						() -> Component.translatable("inventoryfilters.config.sort_mode",
							Component.translatable("inventoryfilters.sort." + InventoryFiltersConfig.modeKey(InventoryFiltersConfig.getSortMode())).withStyle(ChatFormatting.GREEN)),
						() -> InventoryFiltersConfig.setSortMode((InventoryFiltersConfig.getSortMode() + 1) % InventorySorter.MODE_COUNT),
						InventoryFiltersConfig::isEnabled
					),
					Option.toggle("inventoryfilters.config.reverse", InventoryFiltersConfig::isReverseOrder,
						InventoryFiltersConfig::setReverseOrder, InventoryFiltersConfig::isEnabled)
				)
			),
			new Category(
				Component.translatable("inventoryfilters.config.category.hotbar"),
				Component.translatable("inventoryfilters.config.category.hotbar.desc"),
				List.of(Option.toggle("inventoryfilters.config.hotbar", InventoryFiltersConfig::isIncludeHotbar,
					InventoryFiltersConfig::setIncludeHotbar, InventoryFiltersConfig::isEnabled))
			),
			new Category(
				Component.translatable("inventoryfilters.config.category.appearance"),
				Component.translatable("inventoryfilters.config.category.appearance.desc"),
				List.of(new Option(
					() -> Component.translatable("inventoryfilters.config.theme",
						Component.translatable("inventoryfilters.theme." + InventoryFiltersConfig.getButtonTheme().key()).withStyle(ChatFormatting.GREEN)),
					() -> InventoryFiltersConfig.setButtonTheme(
						ButtonTheme.values()[(InventoryFiltersConfig.getButtonTheme().ordinal() + 1) % ButtonTheme.values().length]),
					InventoryFiltersConfig::isEnabled
				))
			),
			new Category(
				Component.translatable("inventoryfilters.config.category.hotbar_presets"),
				Component.translatable("inventoryfilters.config.category.hotbar_presets.desc"),
				List.of(
					new Option(
						() -> Component.translatable("inventoryfilters.config.preset_match_mode",
							Component.translatable(InventoryFiltersConfig.isPresetIgnoringEnchantments()
								? "inventoryfilters.config.preset_match.ignore_enchantments"
								: "inventoryfilters.config.preset_match.exact").withStyle(ChatFormatting.GREEN)),
						() -> InventoryFiltersConfig.setPresetIgnoreEnchantments(!InventoryFiltersConfig.isPresetIgnoringEnchantments()),
						InventoryFiltersConfig::isEnabled
					),
					Option.toggle("inventoryfilters.config.block_autorefill", InventoryFiltersConfig::isBlockAutoRefillEnabled,
						InventoryFiltersConfig::setBlockAutoRefillEnabled, InventoryFiltersConfig::isEnabled)
				)
			)
		);
	}
}
