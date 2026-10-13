package com.dstsoftware.inventoryfilters.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.dstsoftware.inventoryfilters.client.gui.ButtonTheme;
import com.dstsoftware.inventoryfilters.inventory.InventorySorter;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Per-player client settings. They are sent along with each request, so they apply the same way
 * in single player and on any server that has the mod installed.
 */
public final class InventoryFiltersConfig {
	private static final String ENABLED_KEY = "enabled";
	private static final String SORT_MODE_KEY = "sortMode";
	private static final String REVERSE_KEY = "reverseOrder";
	private static final String INCLUDE_HOTBAR_KEY = "includeHotbar";
	private static final String BUTTON_THEME_KEY = "buttonTheme";
	private static final String[] MODE_NAMES = {"type", "name", "category", "rarity"};
	private static final Properties VALUES = new Properties();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("inventoryfilters.properties");
	private static boolean loaded;

	private InventoryFiltersConfig() {}

	public static synchronized void load() {
		if (loaded) return;
		try {
			if (Files.exists(FILE)) {
				try (InputStream input = Files.newInputStream(FILE)) {
					VALUES.load(input);
				}
			}
			VALUES.putIfAbsent(ENABLED_KEY, "true");
			VALUES.putIfAbsent(SORT_MODE_KEY, MODE_NAMES[InventorySorter.MODE_TYPE]);
			VALUES.putIfAbsent(REVERSE_KEY, "false");
			VALUES.putIfAbsent(INCLUDE_HOTBAR_KEY, "false");
			VALUES.putIfAbsent(BUTTON_THEME_KEY, ButtonTheme.LIGHT.key());
			loaded = true;
			save();
		} catch (IOException exception) {
			throw new IllegalStateException("Could not load Inventory Filters settings from " + FILE, exception);
		}
	}

	/** Master switch: when off, no buttons are shown anywhere and nothing is sent to the server. */
	public static synchronized boolean isEnabled() {
		ensureLoaded();
		return Boolean.parseBoolean(VALUES.getProperty(ENABLED_KEY));
	}

	public static synchronized void setEnabled(boolean enabled) {
		set(ENABLED_KEY, Boolean.toString(enabled));
	}

	/** One of InventorySorter.MODE_*: the single way the sort button orders items. */
	public static synchronized int getSortMode() {
		ensureLoaded();
		String stored = VALUES.getProperty(SORT_MODE_KEY, "");
		for (int mode = 0; mode < MODE_NAMES.length; mode++) {
			if (MODE_NAMES[mode].equalsIgnoreCase(stored.trim())) return mode;
		}
		return InventorySorter.MODE_TYPE;
	}

	public static synchronized void setSortMode(int mode) {
		set(SORT_MODE_KEY, modeKey(mode));
	}

	public static String modeKey(int mode) {
		return MODE_NAMES[Math.floorMod(mode, MODE_NAMES.length)];
	}

	public static synchronized boolean isReverseOrder() {
		ensureLoaded();
		return Boolean.parseBoolean(VALUES.getProperty(REVERSE_KEY));
	}

	public static synchronized void setReverseOrder(boolean reverse) {
		set(REVERSE_KEY, Boolean.toString(reverse));
	}

	public static synchronized boolean isIncludeHotbar() {
		ensureLoaded();
		return Boolean.parseBoolean(VALUES.getProperty(INCLUDE_HOTBAR_KEY));
	}

	public static synchronized void setIncludeHotbar(boolean include) {
		set(INCLUDE_HOTBAR_KEY, Boolean.toString(include));
	}

	/** Look of the buttons: Light (default) or Dark. */
	public static synchronized ButtonTheme getButtonTheme() {
		ensureLoaded();
		return ButtonTheme.fromKey(VALUES.getProperty(BUTTON_THEME_KEY, ButtonTheme.LIGHT.key()));
	}

	public static synchronized void setButtonTheme(ButtonTheme theme) {
		set(BUTTON_THEME_KEY, theme.key());
	}

	/** Restores every setting to its fresh-install default. */
	public static synchronized void resetToDefaults() {
		ensureLoaded();
		VALUES.setProperty(ENABLED_KEY, "true");
		VALUES.setProperty(SORT_MODE_KEY, MODE_NAMES[InventorySorter.MODE_TYPE]);
		VALUES.setProperty(REVERSE_KEY, "false");
		VALUES.setProperty(INCLUDE_HOTBAR_KEY, "false");
		VALUES.setProperty(BUTTON_THEME_KEY, ButtonTheme.LIGHT.key());
		save();
	}

	private static void set(String key, String value) {
		ensureLoaded();
		VALUES.setProperty(key, value);
		save();
	}

	private static void ensureLoaded() {
		if (!loaded) load();
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (OutputStream output = Files.newOutputStream(FILE)) {
				VALUES.store(output, "Inventory Filters settings");
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not save Inventory Filters settings to " + FILE, exception);
		}
	}
}
