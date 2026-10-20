package io.github.dannygt97.holoprint.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The options of the mod, kept in {@code config/holoprint.json}. Every change made in the
 * configuration window is saved at once; a missing or broken file simply gives the defaults.
 */
public final class HoloprintConfig {
	public static final int DEFAULT_OPACITY = 60;
	public static final int DEFAULT_HOLOGRAM_COLOR = 0xFFFFFF;
	public static final int DEFAULT_SELECTION_COLOR = 0xFFD166;
	public static final int DEFAULT_FLOW_COLOR = 0x4A4A44;
	public static final int DEFAULT_OUTLINE_COLOR = 0x33B3E6;
	public static final int DEFAULT_MAX_PLACEMENTS = 8;
	public static final int DEFAULT_DRAW_DISTANCE = 256;
	public static final int DEFAULT_BLOCK_BUDGET = 200_000;
	public static final int DEFAULT_COPY_LIMIT = 8_000_000;
	/** Blocks per second while the use button is held (the most is one per game tick). */
	public static final int DEFAULT_FILL_SPEED = 7;
	public static final int MAX_FILL_SPEED = 20;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static HoloprintConfig instance;

	/** Show the key legend in the corner while the editor is open. */
	public boolean showLegend = true;
	/** Ask for the delete key to be pressed twice before blocks are removed. */
	public boolean confirmDelete = true;
	/** Draw the holograms at all. */
	public boolean showHolograms = true;
	/** Draw the entities (armor stands, item frames, animals...) that a schematic contains, frozen. */
	public boolean showEntities = true;
	public boolean biomeColors = true;
	public boolean showBlockEntities = true;
	/** Using an item while looking at a hologram places the block it asks for, in the state it asks for. */
	public boolean easyPlace = true;
	/** Holding the use button keeps placing the blocks of the hologram that are within reach. */
	public boolean fillWhileHeld = true;
	/** Easy placement also puts a block where nothing is next to it to place it against. */
	public boolean placeWithoutSupport = true;
	/** Leave out of the holograms the blocks that are already built in the world. */
	public boolean hideBuilt = true;
	/** Outline the blocks that are the wrong block or in the wrong state. */
	public boolean showDifferences = true;
	/** Also outline blocks that stand where the hologram asks for nothing. */
	public boolean showExtra = false;
	/** How solid the holograms look, 10 to 100 per cent. */
	public int hologramOpacity = DEFAULT_OPACITY;
	/** Water and lava show the game's own moving textures; off, they are drawn as still, flat-coloured blocks. */
	public boolean fluidAnimations = true;
	/** Draw the hologram from meshes kept in the graphics card (fast), instead of copying every quad each frame. */
	public boolean retainedMeshes = true;
	/** Arrows on the water and lava that flows, showing which way it runs. */
	public boolean showFlowArrows = true;
	/** Pointing at a chest (or other container) of a hologram lists what is saved inside it. */
	public boolean showContainerContents = true;
	/** Tint of the holograms, as 0xRRGGBB. */
	public int hologramColor = DEFAULT_HOLOGRAM_COLOR;
	/** Colour of the selection box, as 0xRRGGBB. */
	public int selectionColor = DEFAULT_SELECTION_COLOR;
	/** Colour of the arrows that show which way water and lava flow, as 0xRRGGBB. */
	public int flowColor = DEFAULT_FLOW_COLOR;
	/** Colour of the outline drawn around the blocks of a hologram, as 0xRRGGBB. */
	public int outlineColor = DEFAULT_OUTLINE_COLOR;
	/** While the free camera is on, the hotbar shows the editor's tools and the number keys use them. */
	public boolean hotbarTools = true;
	/** Draw an outline around every block of the holograms, as Litematica does. */
	public boolean blockOutlines = true;
	/** How many schematic placements can exist at once. */
	public int maxPlacements = DEFAULT_MAX_PLACEMENTS;
	/** How far from the camera hologram blocks are drawn. */
	public int drawDistance = DEFAULT_DRAW_DISTANCE;
	/** The most hologram blocks drawn in one frame. */
	public int blockBudget = DEFAULT_BLOCK_BUDGET;
	/** How many blocks per second the fill places while the use button is held. */
	public int fillSpeed = DEFAULT_FILL_SPEED;
	/** The biggest area, in blocks, that can be copied in one go. */
	public int copyLimit = DEFAULT_COPY_LIMIT;

	public static HoloprintConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("holoprint.json");
	}

	private static HoloprintConfig load() {
		try {
			Path file = file();
			if (Files.isRegularFile(file)) {
				HoloprintConfig loaded = GSON.fromJson(Files.readString(file), HoloprintConfig.class);
				if (loaded != null) {
					// The limits of the old way of drawing (160 blocks, 60 000 blocks) were set when every block cost time each frame; with the meshes kept in the
					// graphics card they are far too low, so a file that still has those exact values gets the new ones.
					if (loaded.drawDistance == 160 && loaded.blockBudget == 60_000) {
						loaded.drawDistance = DEFAULT_DRAW_DISTANCE;
						loaded.blockBudget = DEFAULT_BLOCK_BUDGET;
					}
					loaded.clamp();
					return loaded;
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// A damaged file is replaced by the defaults the next time something is saved.
		}
		return new HoloprintConfig();
	}

	public void save() {
		clamp();
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(this));
		} catch (IOException ignored) {
			// The options still work for this session; they just are not remembered.
		}
	}

	/** Puts every option back to its default. */
	public void reset() {
		HoloprintConfig defaults = new HoloprintConfig();
		showLegend = defaults.showLegend;
		confirmDelete = defaults.confirmDelete;
		showHolograms = defaults.showHolograms;
		showEntities = defaults.showEntities;
		biomeColors = defaults.biomeColors;
		showBlockEntities = defaults.showBlockEntities;
		easyPlace = defaults.easyPlace;
		fillWhileHeld = defaults.fillWhileHeld;
		placeWithoutSupport = defaults.placeWithoutSupport;
		hideBuilt = defaults.hideBuilt;
		showDifferences = defaults.showDifferences;
		showExtra = defaults.showExtra;
		hologramOpacity = defaults.hologramOpacity;
		fluidAnimations = defaults.fluidAnimations;
		retainedMeshes = defaults.retainedMeshes;
		showFlowArrows = defaults.showFlowArrows;
		showContainerContents = defaults.showContainerContents;
		hologramColor = defaults.hologramColor;
		selectionColor = defaults.selectionColor;
		flowColor = defaults.flowColor;
		outlineColor = defaults.outlineColor;
		blockOutlines = defaults.blockOutlines;
		hotbarTools = defaults.hotbarTools;
		maxPlacements = defaults.maxPlacements;
		drawDistance = defaults.drawDistance;
		blockBudget = defaults.blockBudget;
		fillSpeed = defaults.fillSpeed;
		copyLimit = defaults.copyLimit;
		save();
	}

	private void clamp() {
		hologramOpacity = Math.max(10, Math.min(100, hologramOpacity));
		hologramColor &= 0xFFFFFF;
		selectionColor &= 0xFFFFFF;
		flowColor &= 0xFFFFFF;
		outlineColor &= 0xFFFFFF;
		maxPlacements = Math.max(1, Math.min(32, maxPlacements));
		drawDistance = Math.max(32, Math.min(512, drawDistance));
		blockBudget = Math.max(10_000, Math.min(250_000, blockBudget));
		fillSpeed = Math.max(1, Math.min(MAX_FILL_SPEED, fillSpeed));
		copyLimit = Math.max(100_000, Math.min(50_000_000, copyLimit));
	}

	/** The colour the holograms are drawn with, as ARGB. */
	public int hologramArgb() {
		int alpha = Math.round(hologramOpacity * 255 / 100.0F);
		return (alpha << 24) | hologramColor;
	}
}
