package com.dannygt97.hastemine.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings for the mining changes. The mining speed is computed on both sides (the client predicts
 * a break, the server decides it), so each side reads its own copy of this file: in multiplayer the
 * server's settings are the ones that count and clients with the mod should match them.
 */
public final class HastemineConfig {
	private static final String ENABLED_KEY = "enabled";
	private static final String DEEPSLATE_INSTANT_KEY = "deepslateInstantWithHaste2";
	private static final String DEEPSLATE_STONE_SPEED_KEY = "deepslateAtStoneSpeed";
	private static final String COBBLESTONE_INSTANT_KEY = "cobblestoneInstantWithHaste2";
	private static final String LOGS_HASTE_KEY = "logsBedrockHaste";
	private static final Properties VALUES = new Properties();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hastemine.properties");
	/** The file this mod used under its old name, Hastestone: read once when there is no new file yet. */
	private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir().resolve("hastestone.properties");
	private static boolean loaded;

	private HastemineConfig() {}

	public static synchronized void load() {
		if (loaded) return;
		try {
			Path source = Files.exists(FILE) ? FILE : LEGACY_FILE;
			if (Files.exists(source)) {
				try (InputStream input = Files.newInputStream(source)) {
					VALUES.load(input);
				}
			}
			// Defaults for a fresh install; keys already present in an existing file are kept.
			VALUES.putIfAbsent(ENABLED_KEY, "true");
			VALUES.putIfAbsent(DEEPSLATE_INSTANT_KEY, "true");
			VALUES.putIfAbsent(DEEPSLATE_STONE_SPEED_KEY, "false");
			VALUES.putIfAbsent(COBBLESTONE_INSTANT_KEY, "false");
			VALUES.putIfAbsent(LOGS_HASTE_KEY, "true");
			loaded = true;
			save();
		} catch (IOException exception) {
			throw new IllegalStateException("Could not load Hastemine settings from " + FILE, exception);
		}
	}

	/** Master switch: when off, mining is exactly vanilla. */
	public static synchronized boolean isEnabled() {
		return get(ENABLED_KEY);
	}

	/** Deepslate breaks at once with Haste II whenever stone would. */
	public static synchronized boolean isDeepslateInstantWithHaste2() {
		return get(DEEPSLATE_INSTANT_KEY);
	}

	/** Deepslate takes as long to mine as stone with the same tool, beacon or not. */
	public static synchronized boolean isDeepslateAtStoneSpeed() {
		return get(DEEPSLATE_STONE_SPEED_KEY);
	}

	/** Cobblestone breaks at once with Haste II whenever stone would. */
	public static synchronized boolean isCobblestoneInstantWithHaste2() {
		return get(COBBLESTONE_INSTANT_KEY);
	}

	/** Overworld logs are mined with Bedrock Edition's Haste scaling, which makes them break at once with Haste II and a good axe. */
	public static synchronized boolean isLogsBedrockHaste() {
		return get(LOGS_HASTE_KEY);
	}

	public static synchronized void setEnabled(boolean value) {
		set(ENABLED_KEY, value);
	}

	public static synchronized void setDeepslateInstantWithHaste2(boolean value) {
		set(DEEPSLATE_INSTANT_KEY, value);
	}

	public static synchronized void setDeepslateAtStoneSpeed(boolean value) {
		set(DEEPSLATE_STONE_SPEED_KEY, value);
	}

	public static synchronized void setCobblestoneInstantWithHaste2(boolean value) {
		set(COBBLESTONE_INSTANT_KEY, value);
	}

	public static synchronized void setLogsBedrockHaste(boolean value) {
		set(LOGS_HASTE_KEY, value);
	}

	/** Restores every setting to its fresh-install default. */
	public static synchronized void resetToDefaults() {
		ensureLoaded();
		VALUES.setProperty(ENABLED_KEY, "true");
		VALUES.setProperty(DEEPSLATE_INSTANT_KEY, "true");
		VALUES.setProperty(DEEPSLATE_STONE_SPEED_KEY, "false");
		VALUES.setProperty(COBBLESTONE_INSTANT_KEY, "false");
		VALUES.setProperty(LOGS_HASTE_KEY, "true");
		save();
	}

	private static boolean get(String key) {
		ensureLoaded();
		return Boolean.parseBoolean(VALUES.getProperty(key));
	}

	private static void set(String key, boolean value) {
		ensureLoaded();
		VALUES.setProperty(key, Boolean.toString(value));
		save();
	}

	private static void ensureLoaded() {
		if (!loaded) load();
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (OutputStream output = Files.newOutputStream(FILE)) {
				VALUES.store(output, "Hastemine settings");
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not save Hastemine settings to " + FILE, exception);
		}
	}
}
