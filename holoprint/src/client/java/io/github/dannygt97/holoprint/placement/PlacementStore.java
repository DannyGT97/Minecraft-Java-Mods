package io.github.dannygt97.holoprint.placement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.hologram.LayerView;
import io.github.dannygt97.holoprint.schematic.BlockStateTags;
import io.github.dannygt97.holoprint.schematic.LitematicFormat;
import io.github.dannygt97.holoprint.schematic.Schematic;
import io.github.dannygt97.holoprint.schematic.SchematicFormats;
import io.github.dannygt97.holoprint.ui.Notices;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Remembers the placements of each world: which schematic, where, turned how, with which name and switches. They are written to
 * {@code config/holoprint/worlds/<world>/placements.json} a moment after any change and when leaving the world, and put back when the
 * world is entered again. A placement that only exists in memory (a pasted copy) is saved next to it as a {@code .litematic}. A
 * placement whose file has disappeared is not restored, but its entry is kept so it comes back if the file does.
 */
public final class PlacementStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** How often (ticks) the placements are compared with what was last written. */
	private static final int CHECK_TICKS = 20;

	/** Folder of the world the player is in, or null outside a world. */
	private static Path folder;
	private static String lastWritten = "";
	/** Placements still being read; nothing is written meanwhile, or it would forget the ones not back yet. */
	private static int loading;
	/** Entries whose file was missing, written again untouched. */
	private static final List<JsonObject> LOST = new ArrayList<>();
	/** Schematics that only existed in memory and are now written, with their file; the set holds those being written. */
	private static final Map<Schematic, Path> WRITTEN = new IdentityHashMap<>();
	private static final Set<Schematic> WRITING = Collections.newSetFromMap(new IdentityHashMap<>());
	private static int counter;
	private static int ticks;

	private PlacementStore() {}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> enter(mc)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> leave());
		ClientTickEvents.END_CLIENT_TICK.register(PlacementStore::tick);
	}

	// --- entering and leaving a world ---

	/** Called on joining a world: puts back the placements saved for it. */
	static void enter(Minecraft mc) {
		HologramRenderer.hide();
		LOST.clear();
		WRITTEN.clear();
		WRITING.clear();
		lastWritten = "";
		LayerView.reset();
		folder = worldFolder(mc);
		if (folder != null) {
			restore(mc);
		}
	}

	/** Called on leaving a world: writes the placements and clears them, so the next world does not show them. */
	static void leave() {
		if (folder != null && loading == 0) {
			write(false);
		}
		folder = null;
		HologramRenderer.hide();
	}

	/** A name for the world that stays the same between sessions, or null when there is none. */
	private static Path worldFolder(Minecraft mc) {
		String key = null;
		var server = mc.getSingleplayerServer();
		if (server != null) {
			Path name = server.getWorldPath(LevelResource.ROOT).normalize().getFileName();
			key = name == null ? null : "world-" + name;
		} else if (mc.getCurrentServer() != null) {
			key = "server-" + mc.getCurrentServer().ip;
		}
		if (key == null) {
			return null;
		}
		return FabricLoader.getInstance().getConfigDir().resolve("holoprint").resolve("worlds").resolve(key.replaceAll("[^A-Za-z0-9._-]", "_"));
	}

	// --- saving ---

	private static void tick(Minecraft mc) {
		if (folder == null || loading > 0 || mc.level == null || ++ticks < CHECK_TICKS) {
			return;
		}
		ticks = 0;
		write(true);
	}

	/** Writes the placements if they differ from what was last written. */
	private static void write(boolean background) {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray array = new JsonArray();
		List<Hologram> all = HologramRenderer.everywhere();
		int active = -1;
		for (Hologram hologram : all) {
			Path file = fileOf(hologram.schematic(), background);
			if (file == null) {
				continue;
			}
			if (hologram == HologramRenderer.activeAnywhere()) {
				active = array.size();
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("file", file.toString());
			entry.addProperty("x", hologram.origin().getX());
			entry.addProperty("y", hologram.origin().getY());
			entry.addProperty("z", hologram.origin().getZ());
			entry.addProperty("turns", hologram.quarterTurns());
			entry.addProperty("mirrored", hologram.mirrored());
			entry.addProperty("label", hologram.label());
			entry.addProperty("dimension", hologram.dimension());
			entry.addProperty("visible", hologram.visible());
			entry.addProperty("enabled", hologram.enabled());
			entry.addProperty("locked", hologram.locked());
			entry.addProperty("complete", hologram.markedComplete());
			entry.addProperty("reopened", hologram.reopened());
			Object[] built = hologram.builtState();
			if (built != null) {
				// where it stood when a Build of it ended: what the client cannot see of the world is still taken as built after a restart
				JsonObject at = new JsonObject();
				BlockPos b = (BlockPos) built[0];
				at.addProperty("x", b.getX());
				at.addProperty("y", b.getY());
				at.addProperty("z", b.getZ());
				at.addProperty("turns", (int) built[1]);
				at.addProperty("mirrored", (boolean) built[2]);
				entry.add("builtAt", at);
			}
			entry.addProperty("showEntities", hologram.showEntities());
			if (!hologram.replacements().isEmpty()) {
				JsonArray replaced = new JsonArray();
				for (Hologram.Replacement replacement : hologram.replacements()) {
					JsonObject r = new JsonObject();
					r.addProperty("from", stateText(replacement.from()));
					r.addProperty("to", stateText(replacement.to()));
					r.addProperty("anyState", replacement.anyState());
					replaced.add(r);
				}
				entry.add("replacements", replaced);
			}
			array.add(entry);
		}
		for (JsonObject lost : LOST) {
			array.add(lost);
		}
		root.addProperty("active", active);
		root.addProperty("layerMode", LayerView.mode().name());
		root.addProperty("layer", LayerView.layer());
		root.add("placements", array);
		String json = GSON.toJson(root);
		if (json.equals(lastWritten) || (array.isEmpty() && !Files.exists(folder.resolve("placements.json")))) {
			return;
		}
		try {
			Files.createDirectories(folder);
			Files.writeString(folder.resolve("placements.json"), json);
			lastWritten = json;
		} catch (IOException ignored) {
			// Nothing is lost for this session; it is tried again at the next change.
		}
	}

	/** The file to remember a schematic by: its own, or a copy written beside the list for one that only lives in memory. Null while the copy is being written. */
	private static Path fileOf(Schematic schematic, boolean background) {
		if (schematic.source() != null && Files.isRegularFile(schematic.source())) {
			return schematic.source().toAbsolutePath();
		}
		Path written = WRITTEN.get(schematic);
		if (written != null) {
			return written;
		}
		if (WRITING.contains(schematic)) {
			return null;
		}
		Path target = folder.resolve("copy-" + System.currentTimeMillis() + "-" + (counter++) + ".litematic");
		Runnable job = () -> {
			try {
				Files.createDirectories(target.getParent());
				LitematicFormat.write(schematic, target);
				Minecraft.getInstance().execute(() -> {
					WRITTEN.put(schematic, target);
					WRITING.remove(schematic);
				});
			} catch (IOException | RuntimeException e) {
				Minecraft.getInstance().execute(() -> WRITING.remove(schematic));
			}
		};
		if (background) {
			WRITING.add(schematic);
			new Thread(job, "Holoprint placement saver").start();
			return null;
		}
		// Leaving the world: the game is about to drop everything, so it is written now.
		try {
			Files.createDirectories(target.getParent());
			LitematicFormat.write(schematic, target);
			WRITTEN.put(schematic, target);
			return target;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	// --- restoring ---

	private static void restore(Minecraft mc) {
		Path list = folder.resolve("placements.json");
		if (!Files.isRegularFile(list)) {
			return;
		}
		JsonObject root;
		try {
			root = JsonParser.parseString(Files.readString(list)).getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			return;
		}
		JsonArray array = root.has("placements") ? root.getAsJsonArray("placements") : new JsonArray();
		// The layer that was being shown comes back with the placements.
		LayerView.restore("SINGLE".equals(string(root, "layerMode", "ALL")), integer(root, "layer", 0));
		int activeIndex = root.has("active") ? root.get("active").getAsInt() : -1;
		int wanted = 0;
		List<Hologram> restored = new ArrayList<>(Collections.nCopies(array.size(), null));
		int[] pending = {0};
		for (int i = 0; i < array.size(); i++) {
			JsonObject entry = array.get(i).getAsJsonObject();
			Path file = Path.of(string(entry, "file", ""));
			if (!Files.isRegularFile(file)) {
				LOST.add(entry);
				Notices.warning(Component.translatable("holoprint.message.restore_missing", file.getFileName() == null ? file.toString() : file.getFileName().toString()));
				continue;
			}
			wanted++;
			pending[0]++;
			loading++;
			final int index = i;
			new Thread(() -> {
				Hologram hologram = null;
				try {
					Schematic schematic = SchematicFormats.read(file);
					schematic.source(file);
					if (schematic.name() == null || schematic.name().isBlank() || schematic.name().equals("Unnamed")) {
						schematic.name(file.getFileName().toString().replaceFirst("\\.[^.]+$", ""));
					}
					hologram = Hologram.placed(schematic, new BlockPos(integer(entry, "x", 0), integer(entry, "y", 0), integer(entry, "z", 0)), integer(entry, "turns", 0), bool(entry, "mirrored", false), replacementsOf(entry));
				} catch (IOException | RuntimeException ignored) {
					// Unreadable now: kept as lost below.
				}
				Hologram result = hologram;
				mc.execute(() -> {
					if (result != null && folder != null) {
						// saved before placements knew their dimension: it belongs to the one the world is loaded in
						result.dimension(string(entry, "dimension", Hologram.currentDimension()));
						result.label(string(entry, "label", ""));
						result.visible(bool(entry, "visible", true));
						result.enabled(bool(entry, "enabled", true));
						result.locked(bool(entry, "locked", false));
						result.markedComplete(bool(entry, "complete", false));
						result.reopened(bool(entry, "reopened", false));
						if (entry.get("builtAt") instanceof JsonObject at) {
							result.restoreBuilt(new BlockPos(integer(at, "x", 0), integer(at, "y", 0), integer(at, "z", 0)), integer(at, "turns", 0), bool(at, "mirrored", false));
						}
						result.showEntities(bool(entry, "showEntities", true));
						restored.set(index, result);
					} else if (result == null) {
						LOST.add(entry);
					}
					loading--;
					if (--pending[0] == 0) {
						finish(mc, restored, activeIndex);
					}
				});
			}, "Holoprint placement restore").start();
		}
		if (wanted == 0) {
			lastWritten = "";
		}
	}

	private static void finish(Minecraft mc, List<Hologram> restored, int activeIndex) {
		if (folder == null) {
			return;
		}
		int count = 0;
		for (int i = 0; i < restored.size(); i++) {
			Hologram hologram = restored.get(i);
			if (hologram != null) {
				HologramRenderer.add(hologram);
				if (i == activeIndex) {
					HologramRenderer.makeActive(hologram);
				}
				count++;
			}
		}
		if (count > 0) {
			Editor.message(mc, Component.translatable("holoprint.message.restored", count));
		}
		write(true);
	}

	// --- replacements ---

	/** A state written the way commands do, {@code minecraft:oak_stairs[facing=north,half=bottom]}. */
	private static String stateText(BlockState state) {
		CompoundTag tag = BlockStateTags.write(state);
		StringBuilder text = new StringBuilder(BlockStateTags.nameOf(tag));
		CompoundTag properties = tag.getCompound("Properties").orElse(null);
		if (properties != null && !properties.isEmpty()) {
			text.append('[');
			boolean first = true;
			for (String key : properties.keySet()) {
				text.append(first ? "" : ",").append(key).append('=').append(properties.getStringOr(key, ""));
				first = false;
			}
			text.append(']');
		}
		return text.toString();
	}

	/** The replacements a saved placement had; one whose block this game does not know is left out. */
	private static List<Hologram.Replacement> replacementsOf(JsonObject entry) {
		List<Hologram.Replacement> list = new ArrayList<>();
		if (entry.get("replacements") instanceof JsonArray array) {
			for (JsonElement element : array) {
				try {
					JsonObject r = element.getAsJsonObject();
					list.add(new Hologram.Replacement(BlockStateTags.read(BlockStateTags.parse(string(r, "from", ""))),
						BlockStateTags.read(BlockStateTags.parse(string(r, "to", ""))), bool(r, "anyState", false)));
				} catch (RuntimeException ignored) {
					// A block this game no longer has: that replacement is dropped.
				}
			}
		}
		return list;
	}

	// --- json helpers ---

	private static String string(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? fallback : e.getAsString();
	}

	private static int integer(JsonObject o, String key, int fallback) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? fallback : e.getAsInt();
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? fallback : e.getAsBoolean();
	}
}
