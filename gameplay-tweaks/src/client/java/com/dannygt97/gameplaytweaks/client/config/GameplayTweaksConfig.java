package com.dannygt97.gameplaytweaks.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

public final class GameplayTweaksConfig {
	private static final String OVERWORLD_FOG = "fog.overworld";
	private static final String NETHER_FOG = "fog.nether";
	private static final String END_FOG = "fog.end";
	private static final String FULLBRIGHT = "fullbright";
	private static final Properties VALUES = new Properties();
	private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
	private static final Path FILE = CONFIG_DIR.resolve("gameplaytweaks.properties");
	private static final Path LEGACY_FILE = CONFIG_DIR.resolve("visualtweaks.properties");
	private static boolean loaded;

	private GameplayTweaksConfig() {}

	public static synchronized void load() {
		if (loaded) return;
		try {
			Path source = Files.exists(FILE) ? FILE : LEGACY_FILE;
			if (Files.exists(source)) try (InputStream in = Files.newInputStream(source)) { VALUES.load(in); }
			VALUES.remove("bridging");
			VALUES.putIfAbsent(OVERWORLD_FOG, "false");
			VALUES.putIfAbsent(NETHER_FOG, "false");
			VALUES.putIfAbsent(END_FOG, "false");
			VALUES.putIfAbsent(FULLBRIGHT, "false");
			loaded = true;
			save();
		} catch (IOException e) {
			throw new IllegalStateException("Could not load Gameplay Tweaks settings from " + FILE, e);
		}
	}

	public static boolean isFogDisabled(String dimension) {
		return get(switch (dimension) {
			case "overworld" -> OVERWORLD_FOG;
			case "nether" -> NETHER_FOG;
			case "end" -> END_FOG;
			default -> "";
		});
	}

	public static void setFogDisabled(String dimension, boolean value) {
		String key = switch (dimension) {
			case "overworld" -> OVERWORLD_FOG;
			case "nether" -> NETHER_FOG;
			case "end" -> END_FOG;
			default -> throw new IllegalArgumentException("Unknown dimension: " + dimension);
		};
		set(key, value);
	}

	public static boolean isFullbrightEnabled() { return get(FULLBRIGHT); }
	public static void setFullbrightEnabled(boolean value) { set(FULLBRIGHT, value); }
	private static synchronized boolean get(String key) {
		ensureLoaded();
		return !key.isEmpty() && Boolean.parseBoolean(VALUES.getProperty(key, "false"));
	}

	private static synchronized void set(String key, boolean value) {
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
			try (OutputStream out = Files.newOutputStream(FILE)) { VALUES.store(out, "Gameplay Tweaks settings"); }
		} catch (IOException e) {
			throw new IllegalStateException("Could not save Gameplay Tweaks settings to " + FILE, e);
		}
	}
}
