package com.dannygt97.treechopper.tree;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Shared server-side settings for tree felling. */
public final class TreeChopperConfig {
	private static final String ENABLED_KEY = "enabled";
	private static final String ALLOW_WITHOUT_SNEAKING_KEY = "allowWithoutSneaking";
	private static final String NETHER_FELLING_KEY = "netherFungiFelling";
	private static final String AUTO_SAPLING_KEY = "autoSaplingPlacement";
	private static final Properties VALUES = new Properties();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("treechopper.properties");
	private static boolean loaded;

	private TreeChopperConfig() {}

	public static synchronized void load() {
		if (loaded) return;
		try {
			if (Files.exists(FILE)) {
				try (InputStream input = Files.newInputStream(FILE)) {
					VALUES.load(input);
				}
			}
			// Defaults for a fresh install; keys already present in an existing file are kept.
			VALUES.putIfAbsent(ENABLED_KEY, "true");
			VALUES.putIfAbsent(ALLOW_WITHOUT_SNEAKING_KEY, "true");
			VALUES.putIfAbsent(NETHER_FELLING_KEY, "true");
			VALUES.putIfAbsent(AUTO_SAPLING_KEY, "false");
			loaded = true;
			save();
		} catch (IOException exception) {
			throw new IllegalStateException("Could not load Tree Chopper settings from " + FILE, exception);
		}
	}

	/** Master switch: when off, the mod does nothing at all. */
	public static synchronized boolean isEnabled() {
		return get(ENABLED_KEY);
	}

	public static synchronized boolean isAllowWithoutSneaking() {
		return get(ALLOW_WITHOUT_SNEAKING_KEY);
	}

	/** Whether crimson and warped fungi (nether trees) are felled too. */
	public static synchronized boolean isNetherFellingEnabled() {
		return get(NETHER_FELLING_KEY);
	}

	public static synchronized boolean isAutoSaplingPlacement() {
		return get(AUTO_SAPLING_KEY);
	}

	public static synchronized void setEnabled(boolean enabled) {
		set(ENABLED_KEY, enabled);
	}

	public static synchronized void setAllowWithoutSneaking(boolean allow) {
		set(ALLOW_WITHOUT_SNEAKING_KEY, allow);
	}

	public static synchronized void setNetherFellingEnabled(boolean enabled) {
		set(NETHER_FELLING_KEY, enabled);
	}

	public static synchronized void setAutoSaplingPlacement(boolean enabled) {
		set(AUTO_SAPLING_KEY, enabled);
	}

	/** Restores every setting to its fresh-install default. */
	public static synchronized void resetToDefaults() {
		ensureLoaded();
		VALUES.setProperty(ENABLED_KEY, "true");
		VALUES.setProperty(ALLOW_WITHOUT_SNEAKING_KEY, "true");
		VALUES.setProperty(NETHER_FELLING_KEY, "true");
		VALUES.setProperty(AUTO_SAPLING_KEY, "false");
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
				VALUES.store(output, "Tree Chopper settings");
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not save Tree Chopper settings to " + FILE, exception);
		}
	}
}
