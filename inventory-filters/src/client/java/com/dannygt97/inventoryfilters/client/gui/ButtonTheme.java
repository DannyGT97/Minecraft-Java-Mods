package com.dannygt97.inventoryfilters.client.gui;

import net.minecraft.resources.Identifier;

/** Icon and theme selection for inventory and hotbar preset buttons. */
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

	/** Normal preset button icon for this theme. */
	public Identifier presetIcon(String name) {
		return texture("icon_" + name + presetSuffix(name, false));
	}

	/** Hovered/selected preset button icon for this theme. */
	public Identifier selectedPresetIcon(String name) {
		return texture("icon_" + name + presetSuffix(name, true));
	}

	private String presetSuffix(String name, boolean selected) {
		if (this == LIGHT) return selected ? "_selected" : "";
		// The manager icons in the dark folder use the same suffixes as the light
		// set; save/load icons include an explicit _dark suffix.
		if ("manage_presets".equals(name)) return selected ? "_selected" : "";
		return selected ? "_selected_dark" : "_dark";
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
