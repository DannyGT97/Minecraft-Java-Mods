package io.github.dannygt97.holoprint.placement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.dannygt97.holoprint.config.HoloprintConfig;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.schematic.LitematicFormat;
import io.github.dannygt97.holoprint.schematic.Region;
import io.github.dannygt97.holoprint.schematic.Schematic;
import io.github.dannygt97.holoprint.schematic.SchematicFolder;
import io.github.dannygt97.holoprint.selection.Clipboard;
import io.github.dannygt97.holoprint.selection.Selection;
import io.github.dannygt97.holoprint.ui.Notices;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Copy, cut, paste, delete and save as schematic. They are the same whether they come from a key or
 * from a button of the toolbar.
 */
public final class SelectionActions {
	private static final int DELETE_CONFIRM_MS = 4000;
	/**
	 * The server kicks players that send too many chat lines too fast. Every command adds 20 to a
	 * counter that falls by 1 per tick and the limit is 200, so commands are paced to stay below it.
	 */
	private static final int SPAM_COST = 20;
	private static final int SPAM_CEILING = 180;

	private static final Deque<String> PENDING_COMMANDS = new ArrayDeque<>();
	private static int spam;
	private static long deleteConfirmUntil;

	private SelectionActions() {}

	/** True when there is a finished selection to act on. */
	public static boolean hasSelection() {
		return Selection.isComplete();
	}

	/** True when the player may run commands here (cheats on in the world, or operator on a server). */
	public static boolean hasCommandAccess(Minecraft mc) {
		return mc.player != null && mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/**
	 * Deleting and moving change the world with commands, so they exist only in creative mode or when the player may run
	 * commands. In a survival world without cheats Holoprint never sends a command: the world stays legal, and a hologram
	 * is built by hand or with the fill assistant.
	 */
	public static boolean canChangeWorld(Minecraft mc) {
		return mc.player != null && (mc.player.isCreative() || hasCommandAccess(mc));
	}

	/** Queues commands to be sent at the pace the server accepts them. */
	static void queue(List<String> commands) {
		PENDING_COMMANDS.addAll(commands);
	}

	/** How many queued commands have not been sent yet. */
	public static int pendingCommands() {
		return PENDING_COMMANDS.size();
	}

	/** Throws away the commands not sent yet; returns how many there were. */
	public static int cancelPending() {
		int left = PENDING_COMMANDS.size();
		PENDING_COMMANDS.clear();
		return left;
	}

	public static boolean hasClipboard() {
		return !Clipboard.isEmpty();
	}

	/** Copies the selected area; with no selection, the main hologram. */
	public static void copy(Minecraft mc) {
		if (Selection.isComplete()) {
			Schematic schematic = capture(mc);
			if (schematic == null) {
				return;
			}
			Clipboard.set(schematic, 0, false);
			Editor.message(mc, Component.translatable("holoprint.message.copied", schematic.totalBlocks(),
				Selection.sizeX(), Selection.sizeY(), Selection.sizeZ()));
			return;
		}
		Hologram hologram = HologramRenderer.active();
		if (hologram != null) {
			Clipboard.set(hologram);
			Editor.message(mc, Component.translatable("holoprint.message.copied_hologram"));
			return;
		}
		Editor.message(mc, Component.translatable("holoprint.message.nothing_to_copy"));
	}

	/**
	 * With permission to run commands, picks the selected area up to move it: it follows the pointer and a click moves the
	 * real blocks there with /clone (see {@link #cloneMove}). Otherwise copies the area and then asks to delete it.
	 */
	public static void cut(Minecraft mc) {
		if (hasCommandAccess(mc)) {
			if (!Selection.isComplete()) {
				Editor.message(mc, Component.translatable("holoprint.message.no_selection"));
				return;
			}
			Schematic schematic = capture(mc);
			if (schematic == null) {
				return;
			}
			// Putting the carried copy down makes it the selected placement, which drops the selection, so what is needed of it is read now.
			BlockPos min = Selection.min();
			int sizeX = Selection.sizeX(), sizeY = Selection.sizeY(), sizeZ = Selection.sizeZ();
			Hologram hologram = new Hologram(schematic, min);
			Editor.setMode(mc, EditorMode.COPY);
			if (Editor.mode() == EditorMode.COPY) {
				((CopyTool) Editor.tool(EditorMode.COPY)).carryMove(mc, hologram, min, sizeX, sizeY, sizeZ);
				io.github.dannygt97.holoprint.ui.HotbarTools.holding();
			}
			return;
		}
		if (!canChangeWorld(mc)) {
			Editor.message(mc, Component.translatable("holoprint.message.survival_no_commands"));
			return;
		}
		if (!Selection.isComplete()) {
			Editor.message(mc, Component.translatable("holoprint.message.no_selection"));
			return;
		}
		copy(mc);
		delete(mc);
	}

	/** Pastes the clipboard: a copy of it is carried by the pointer, to put down where wanted. */
	public static void paste(Minecraft mc) {
		Hologram hologram = Clipboard.newHologram(BlockPos.ZERO);
		if (hologram == null) {
			Editor.message(mc, Component.translatable("holoprint.message.empty_clipboard"));
			return;
		}
		Editor.setMode(mc, EditorMode.COPY);
		if (Editor.mode() == EditorMode.COPY) {
			((CopyTool) Editor.tool(EditorMode.COPY)).carryFromCamera(mc, hologram);
			io.github.dannygt97.holoprint.ui.HotbarTools.holding();
		}
	}

	/** Deletes the selected area. It asks for the key to be pressed again first, so it is never an accident. */
	public static void delete(Minecraft mc) {
		if (!canChangeWorld(mc)) {
			Editor.message(mc, Component.translatable("holoprint.message.survival_no_commands"));
			return;
		}
		if (!Selection.isComplete()) {
			Editor.message(mc, Component.translatable("holoprint.message.no_selection"));
			return;
		}
		long now = System.currentTimeMillis();
		if (HoloprintConfig.get().confirmDelete && now > deleteConfirmUntil) {
			deleteConfirmUntil = now + DELETE_CONFIRM_MS;
			Editor.message(mc, Component.translatable("holoprint.message.confirm_delete", Selection.volume(),
				EditorHud.name(EditorKeys.DELETE)));
			return;
		}
		deleteConfirmUntil = 0;
		BlockPos min = Selection.min();
		BlockPos max = Selection.max();
		int pieces = 0;
		// 32 x 32 x 32 is exactly the size of one /fill; the area is cut into boxes of that size.
		for (int x = min.getX(); x <= max.getX(); x += 32) {
			for (int y = min.getY(); y <= max.getY(); y += 32) {
				for (int z = min.getZ(); z <= max.getZ(); z += 32) {
					int x2 = Math.min(x + 31, max.getX());
					int y2 = Math.min(y + 31, max.getY());
					int z2 = Math.min(z + 31, max.getZ());
					PENDING_COMMANDS.add("fill " + x + " " + y + " " + z + " " + x2 + " " + y2 + " " + z2 + " air");
					pieces++;
				}
			}
		}
		Editor.message(mc, Component.translatable("holoprint.message.deleting", Selection.volume(), pieces));
	}

	/**
	 * Moves the real blocks of the area that starts at {@code from} and has the given size by what {@code carried} was moved from
	 * there, with /clone in its "move" mode (which keeps the data of chests and the like). The area is cut into boxes of at most
	 * 32 x 32 x 32 (one /clone each), sent in an order where no box lands on another one that has not moved yet. The entities
	 * inside the area (not players) are teleported along.
	 */
	static boolean cloneMove(Minecraft mc, BlockPos from, int sizeX, int sizeY, int sizeZ, Hologram carried) {
		if (!hasCommandAccess(mc)) {
			Editor.message(mc, Component.translatable("holoprint.message.move_no_access"));
			return false;
		}
		if (carried.quarterTurns() != 0 || carried.mirrored()) {
			return moveTurned(mc, from, sizeX, sizeY, sizeZ, carried);
		}
		int dx = carried.origin().getX() - from.getX(), dy = carried.origin().getY() - from.getY(), dz = carried.origin().getZ() - from.getZ();
		if (dx == 0 && dy == 0 && dz == 0) {
			Editor.message(mc, Component.translatable("holoprint.message.move_same"));
			return false;
		}
		java.util.List<int[]> remaining = new java.util.ArrayList<>();
		for (int x = 0; x < sizeX; x += 32) {
			for (int y = 0; y < sizeY; y += 32) {
				for (int z = 0; z < sizeZ; z += 32) {
					remaining.add(new int[] {from.getX() + x, from.getY() + y, from.getZ() + z,
						from.getX() + Math.min(x + 31, sizeX - 1), from.getY() + Math.min(y + 31, sizeY - 1), from.getZ() + Math.min(z + 31, sizeZ - 1)});
				}
			}
		}
		java.util.List<String> commands = new java.util.ArrayList<>();
		while (!remaining.isEmpty()) {
			int[] next = null;
			for (int[] box : remaining) {
				int[] landing = {box[0] + dx, box[1] + dy, box[2] + dz, box[3] + dx, box[4] + dy, box[5] + dz};
				boolean safe = true;
				for (int[] other : remaining) {
					if (other != box && overlaps(landing, other)) {
						safe = false;
						break;
					}
				}
				if (safe) {
					next = box;
					break;
				}
			}
			if (next == null) {
				Editor.message(mc, Component.translatable("holoprint.message.move_overlap"));
				return false;
			}
			remaining.remove(next);
			commands.add("clone " + next[0] + " " + next[1] + " " + next[2] + " " + next[3] + " " + next[4] + " " + next[5] + " "
				+ (next[0] + dx) + " " + (next[1] + dy) + " " + (next[2] + dz) + " replace move");
		}
		AABB area = new AABB(from.getX(), from.getY(), from.getZ(), from.getX() + sizeX, from.getY() + sizeY, from.getZ() + sizeZ);
		if (!mc.level.getEntities((Entity) null, area, e -> !(e instanceof Player)).isEmpty()) {
			commands.add("execute as @e[x=" + from.getX() + ",y=" + from.getY() + ",z=" + from.getZ() + ",dx=" + (sizeX - 1) + ",dy=" + (sizeY - 1) + ",dz=" + (sizeZ - 1)
				+ ",type=!player] at @s run tp @s ~" + dx + " ~" + dy + " ~" + dz);
		}
		PENDING_COMMANDS.addAll(commands);
		Editor.message(mc, Component.translatable("holoprint.message.moving", (long) sizeX * sizeY * sizeZ, commands.size()));
		return true;
	}

	/**
	 * Moves an area that is turned or mirrored on the way. /clone cannot do that, so the area (as it was when picked up, with its
	 * chests and entities) is emptied and built again where the hologram stands, turned.
	 */
	private static boolean moveTurned(Minecraft mc, BlockPos from, int sizeX, int sizeY, int sizeZ, Hologram carried) {
		if (mc.getSingleplayerServer() == null && carried.hasContainers(mc)) {
			// A server tells this game what is written on a sign but not what is inside a chest, so a chest moved this way would lose its items.
			Editor.message(mc, Component.translatable("holoprint.message.move_turned_containers"));
			return false;
		}
		if (!HologramBuilder.build(mc, carried, new HologramBuilder.Area(from, sizeX, sizeY, sizeZ))) {
			Editor.message(mc, Component.translatable("holoprint.message.build_busy"));
			return false;
		}
		return true;
	}

	private static boolean overlaps(int[] a, int[] b) {
		return a[0] <= b[3] && a[3] >= b[0] && a[1] <= b[4] && a[4] >= b[1] && a[2] <= b[5] && a[5] >= b[2];
	}

	/** Saves the selected area as a .litematic file in the schematics folder. */
	public static void save(Minecraft mc) {
		if (!Selection.isComplete()) {
			Notices.warning(Component.translatable("holoprint.message.no_selection"));
			return;
		}
		Schematic schematic;
		try {
			schematic = capture(mc);
		} catch (RuntimeException e) {
			Notices.error(Component.translatable("holoprint.message.save_failed", String.valueOf(e)));
			return;
		}
		if (schematic == null) {
			return;
		}
		String chosen = Selection.name().replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
		String name = chosen.isEmpty() ? "selection-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) : chosen;
		schematic.name(name);
		Path file = SchematicFolder.path().resolve(name + ".litematic");
		try {
			Files.createDirectories(file.getParent());
			LitematicFormat.write(schematic, file);
			Notices.info(Component.translatable("holoprint.message.saved", file.getFileName().toString()));
		} catch (IOException | RuntimeException e) {
			Notices.error(Component.translatable("holoprint.message.save_failed", String.valueOf(e.getMessage())));
		}
	}

	/** Sends the queued delete commands at a pace the server accepts. */
	static void tick(Minecraft mc) {
		if (spam > 0) {
			spam--;
		}
		if (!canChangeWorld(mc)) {
			PENDING_COMMANDS.clear();
			return;
		}
		if (!PENDING_COMMANDS.isEmpty() && spam + SPAM_COST <= SPAM_CEILING) {
			mc.player.connection.sendCommand(PENDING_COMMANDS.poll());
			spam += SPAM_COST;
		}
	}

	/**
	 * Saves the data of the chests, signs and the like at {@code found} into {@code region}. In a world of one's own it is read from the world's
	 * server, which knows what is inside a chest; otherwise from what this client was told, which has the text of a sign but not the
	 * items of a chest (the server never sends them unless the chest is opened).
	 */
	private static void readBlockEntities(Minecraft mc, List<BlockPos> found, BlockPos min, Region region) {
		if (found.isEmpty()) {
			return;
		}
		Map<BlockPos, CompoundTag> fromServer = new HashMap<>();
		MinecraftServer server = mc.getSingleplayerServer();
		if (server != null) {
			ResourceKey<Level> dimension = mc.level.dimension();
			try {
				fromServer = server.submit(() -> {
					Map<BlockPos, CompoundTag> out = new HashMap<>();
					ServerLevel level = server.getLevel(dimension);
					if (level != null) {
						for (BlockPos pos : found) {
							BlockEntity entity = level.getBlockEntity(pos);
							if (entity != null) {
								out.put(pos, entity.saveWithFullMetadata(level.registryAccess()));
							}
						}
					}
					return out;
				}).get(10, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException | RuntimeException e) {
				// The client's own copy is used below.
			}
		}
		for (BlockPos pos : found) {
			CompoundTag data = fromServer.get(pos);
			if (data == null) {
				BlockEntity entity = mc.level.getBlockEntity(pos);
				data = entity == null ? null : entity.saveWithFullMetadata(mc.level.registryAccess());
			}
			if (data != null) {
				region.blockEntities().put(pos.subtract(min), data);
			}
		}
	}

	/** Reads the blocks of the selected area from the world into a schematic. */
	private static Schematic capture(Minecraft mc) {
		if (Selection.volume() > HoloprintConfig.get().copyLimit) {
			Notices.warning(Component.translatable("holoprint.message.too_big", Selection.volume()));
			return null;
		}
		BlockPos min = Selection.min();
		Region region = new Region("selection", BlockPos.ZERO, Selection.sizeX(), Selection.sizeY(), Selection.sizeZ());
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		List<BlockPos> withData = new ArrayList<>();
		for (int y = 0; y < region.sizeY(); y++) {
			for (int z = 0; z < region.sizeZ(); z++) {
				for (int x = 0; x < region.sizeX(); x++) {
					at.set(min.getX() + x, min.getY() + y, min.getZ() + z);
					BlockState state = mc.level.getBlockState(at);
					region.set(x, y, z, state);
					if (state.getBlock() instanceof EntityBlock) {
						withData.add(at.immutable());
					}
				}
			}
		}
		readBlockEntities(mc, withData, min, region);
		// The entities inside the area (not players) come along, with their positions relative to the area.
		AABB box = new AABB(min.getX(), min.getY(), min.getZ(), min.getX() + region.sizeX(), min.getY() + region.sizeY(), min.getZ() + region.sizeZ());
		for (Entity entity : mc.level.getEntities((Entity) null, box, e -> !(e instanceof Player) && e.isAlive())) {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, mc.level.registryAccess());
			if (!entity.save(output)) {
				continue;
			}
			CompoundTag data = output.buildResult();
			ListTag pos = new ListTag();
			pos.add(DoubleTag.valueOf(entity.getX() - min.getX()));
			pos.add(DoubleTag.valueOf(entity.getY() - min.getY()));
			pos.add(DoubleTag.valueOf(entity.getZ() - min.getZ()));
			data.put("Pos", pos);
			region.entities().add(data);
		}
		Schematic schematic = new Schematic();
		schematic.name("selection");
		schematic.author(mc.player.getGameProfile().name());
		long now = System.currentTimeMillis();
		schematic.timeCreated(now);
		schematic.timeModified(now);
		schematic.regions().add(region);
		return schematic;
	}
}
