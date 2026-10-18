package com.dannygt97.inventoryfilters.client.gui;

import net.minecraft.resources.Identifier;

/**
 * The look of the sort/stack/transfer buttons. Each theme is a folder under textures/ holding the
 * four icons (sort, transfer, stack_down, stack_up), each in a normal and a hovered/selected
 * version.
 */
public enum ButtonTheme {
	LIGHT("light", "", "_blue"),
	DARK("dark", "_dark", "_dark_selected");

	private final String folder;
	private final String normalSuffix;
	private final String selectedSuffix;

	ButtonTheme(String folder, String normalSuffix, String selectedSuffix) {
		this.folder = folder;
		this.normalSuffix = normalSuffix;
		this.selectedSuffix = selectedSuffix;
	}

	/** Key used in the config file and in the language files (inventoryfilters.theme.<key>). */
	public String key() {
		return folder;
	}

	public Identifier icon(String name) {
		return texture("icon_" + name + normalSuffix);
	}

	public Identifier selectedIcon(String name) {
		return texture("icon_" + name + selectedSuffix);
	}

	/** Preset icons currently ship in the light asset set only. */
	public Identifier presetIcon(String name) {
		return Identifier.fromNamespaceAndPath("inventoryfilters", "textures/light/icon_" + name + ".png");
	}

	private Identifier texture(String file) {
		return Identifier.fromNamespaceAndPath("inventoryfilters", "textures/" + folder + "/" + file + ".png");
	}

	public static ButtonTheme fromKey(String key) {
		for (ButtonTheme theme : values()) {
			if (theme.folder.equalsIgnoreCase(key.trim())) {
				return theme;
			}
		}
		return LIGHT;
	}
}
