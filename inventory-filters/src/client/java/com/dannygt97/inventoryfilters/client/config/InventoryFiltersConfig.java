package com.dannygt97.inventoryfilters.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.dannygt97.inventoryfilters.client.gui.ButtonTheme;
import com.dannygt97.inventoryfilters.inventory.InventorySorter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import com.mojang.serialization.JsonOps;

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
	private static final String PRESET_IGNORE_ENCHANTMENTS_KEY = "presetIgnoreEnchantments";
	private static final String BLOCK_AUTO_REFILL_KEY = "blockAutoRefill";
	private static final String PRESET_KEY_PREFIX = "hotbarPreset";
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
			VALUES.putIfAbsent(PRESET_IGNORE_ENCHANTMENTS_KEY, "false");
			VALUES.putIfAbsent(BLOCK_AUTO_REFILL_KEY, "false");
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

	public static synchronized boolean isPresetIgnoringEnchantments() {
		ensureLoaded();
		return Boolean.parseBoolean(VALUES.getProperty(PRESET_IGNORE_ENCHANTMENTS_KEY, "false"));
	}

	public static synchronized void setPresetIgnoreEnchantments(boolean ignore) {
		set(PRESET_IGNORE_ENCHANTMENTS_KEY, Boolean.toString(ignore));
	}

	public static synchronized boolean isBlockAutoRefillEnabled() {
		ensureLoaded();
		return Boolean.parseBoolean(VALUES.getProperty(BLOCK_AUTO_REFILL_KEY, "false"));
	}

	public static synchronized void setBlockAutoRefillEnabled(boolean enabled) {
		set(BLOCK_AUTO_REFILL_KEY, Boolean.toString(enabled));
		com.dannygt97.inventoryfilters.client.InventoryFiltersClient.syncBlockAutoRefill();
	}

	/** Saves the current hotbar as JSON-encoded item stacks; preset contents stay in the local client config. */
	public static synchronized boolean saveHotbarPreset(int preset) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) return false;

		RegistryAccess registries = minecraft.level.registryAccess();
		RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
		JsonArray hotbar = new JsonArray();
		for (int i = 0; i < 9; i++) {
			ItemStack stack = minecraft.player.getInventory().getItem(i);
			JsonElement encoded = stack.isEmpty() ? JsonNull.INSTANCE : ItemStack.CODEC.encodeStart(ops, stack)
				.getOrThrow(error -> new IllegalStateException("Could not encode hotbar item: " + error));
			hotbar.add(encoded);
		}
		String encoded = Base64.getEncoder().encodeToString(hotbar.toString().getBytes(StandardCharsets.UTF_8));
		set(presetKey(preset), encoded);
		return true;
	}

	/** Returns the nine encoded item stacks, or an empty list when this preset has not been saved. */
	public static synchronized List<String> getHotbarPreset(int preset) {
		ensureLoaded();
		String encoded = VALUES.getProperty(presetKey(preset));
		if (encoded == null || encoded.isBlank()) return List.of();
		try {
			JsonArray hotbar = JsonParser.parseString(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)).getAsJsonArray();
			if (hotbar.size() != 9) return List.of();
			List<String> result = new ArrayList<>(9);
			for (JsonElement slot : hotbar) result.add(slot == null || slot.isJsonNull() ? "" : slot.toString());
			return List.copyOf(result);
		} catch (RuntimeException exception) {
			return List.of();
		}
	}

	/** Decodes a saved preset with the active world's registries for the manager preview. */
	public static synchronized List<ItemStack> getHotbarPresetStacks(int preset) {
		List<String> encoded = getHotbarPreset(preset);
		if (encoded.isEmpty()) return List.of();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) return List.of();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, minecraft.level.registryAccess());
		List<ItemStack> result = new ArrayList<>(9);
		for (String slot : encoded) {
			if (slot.isEmpty()) {
				result.add(ItemStack.EMPTY);
				continue;
			}
			try {
				ItemStack stack = ItemStack.CODEC.parse(ops, JsonParser.parseString(slot)).result().orElse(ItemStack.EMPTY);
				result.add(isPresetIgnoringEnchantments()
					? com.dannygt97.inventoryfilters.inventory.HotbarPresetMatch.withoutEnchantments(stack)
					: stack);
			} catch (RuntimeException exception) {
				result.add(ItemStack.EMPTY);
			}
		}
		return List.copyOf(result);
	}

	private static String presetKey(int preset) {
		if (preset < 0 || preset >= 3) throw new IllegalArgumentException("Preset index must be between 0 and 2");
		return PRESET_KEY_PREFIX + (preset + 1);
	}

	/** Restores every setting to its fresh-install default. */
	public static synchronized void resetToDefaults() {
		ensureLoaded();
		VALUES.setProperty(ENABLED_KEY, "true");
		VALUES.setProperty(SORT_MODE_KEY, MODE_NAMES[InventorySorter.MODE_TYPE]);
		VALUES.setProperty(REVERSE_KEY, "false");
		VALUES.setProperty(INCLUDE_HOTBAR_KEY, "false");
		VALUES.setProperty(BUTTON_THEME_KEY, ButtonTheme.LIGHT.key());
		VALUES.setProperty(PRESET_IGNORE_ENCHANTMENTS_KEY, "false");
		VALUES.setProperty(BLOCK_AUTO_REFILL_KEY, "false");
		com.dannygt97.inventoryfilters.client.InventoryFiltersClient.syncBlockAutoRefill();
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
