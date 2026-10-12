package com.dstsoftware.dynamiclights.client.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.fabricmc.loader.api.FabricLoader;

// Client settings, stored in config/dynamiclights.json. A missing or corrupt file falls back to the
// defaults and is rewritten; keys missing from an older file simply keep their default.
public final class DynamicLightsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static DynamicLightsConfig instance = new DynamicLightsConfig();

	// Two top-level gates picked per entity by identity: the first applies to the local player's own
	// light (a torch in your hand, you catching fire), the second to every other entity. Neither
	// one touches the individual categories below.
	public boolean playerDynamicLightEnabled = true;
	public boolean entitiesDynamicLightEnabled = true;

	public boolean droppedAndHeldItems = true;
	public boolean glowSquids = true;
	public boolean blazes = true;
	public boolean fireballs = true;
	public boolean magmaCubes = true;
	public boolean spectralArrows = true;
	public boolean allays = true;

	public boolean entitiesOnFire = true;
	public boolean wardenSonicBoom = true;
	// OFF, FAST (a single point of light at the middle of the beam) or FANCY (the whole beam).
	public GuardianLaserMode guardianLaserMode = GuardianLaserMode.FANCY;
	// OFF, SIMPLE (constant brightness) or FANCY (ramps up as the creeper swells / the fuse burns).
	public DynamicLightMode creeperSwellMode = DynamicLightMode.FANCY;
	public DynamicLightMode tntMode = DynamicLightMode.FANCY;

	public enum GuardianLaserMode {
		OFF,
		FAST,
		FANCY
	}

	public enum DynamicLightMode {
		OFF,
		SIMPLE,
		FANCY
	}

	private DynamicLightsConfig() {
	}

	public static DynamicLightsConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("dynamiclights.json");
	}

	public static void load() {
		Path path = file();
		instance = new DynamicLightsConfig();
		if (Files.exists(path)) {
			try {
				DynamicLightsConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), DynamicLightsConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | JsonSyntaxException e) {
				instance = new DynamicLightsConfig();
			}
		}
		save();
	}

	/** Restores every setting to its default. */
	public static void reset() {
		instance = new DynamicLightsConfig();
		save();
	}

	public static void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(instance), StandardCharsets.UTF_8);
		} catch (IOException e) {
			// Non-fatal: the setting just won't survive a restart.
		}
	}
}
