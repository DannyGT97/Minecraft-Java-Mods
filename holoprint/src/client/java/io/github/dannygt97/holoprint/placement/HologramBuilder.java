package io.github.dannygt97.holoprint.placement;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Builds the whole active hologram in the world at once, for a player who may run commands (cheats on): every block of it, in
 * the state it has, with the data of chests, signs and the like, the entities of the schematic, and no materials spent. It is a
 * different thing from copying a hologram, which only makes another hologram.
 *
 * <p>In a world of one's own the blocks are written by the world's own server, in batches so a big hologram does not stall the game.
 * On any other server they are sent as commands ({@code /fill} for runs of identical blocks, {@code /setblock} for the ones with data,
 * {@code /summon} for the entities), at the pace the server accepts commands. Air cells of the hologram leave the world as it is.
 * Pressing the key asks for a second press first, like deleting; pressing it while commands are still going out stops them.
 */
public final class HologramBuilder {
	private static final int CONFIRM_MS = 4000;
	/** Blocks written per server tick. */
	private static final int BATCH = 20_000;
	/** Written without neighbour updates (the hologram already has the connections right) and without drops. */
	private static final int FLAGS = 2 | 16 | 32;
	/** The most blocks one /fill or /clone may touch by default. */
	private static final int FILL_LIMIT = 32_768;
	/** The longest command the game accepts. */
	private static final int MAX_COMMAND = 32_000;

	/** For the tests: build with commands even in a world of one's own. */
	public static boolean forceCommands;

	private static long confirmUntil;
	private static Hologram confirming;
	private static volatile boolean busy;
	/** True while the commands of a build are still going out. */
	private static boolean commandBuild;

	private HologramBuilder() {}

	/** How far along the pointer a hologram is looked for. */
	private static final double PICK_DISTANCE = 256.0;

	/** A box of the world to empty before building (when a hologram stands in for the area it was made from). */
	public record Area(BlockPos min, int sizeX, int sizeY, int sizeZ) {}

	/**
	 * The key: the first press asks, a second press within a few seconds builds. The hologram built is the one the pointer is over
	 * (so each pasted copy can be built in turn), or else the one last used.
	 */
	public static void request(Minecraft mc, Vec3 ray) {
		if (commandBuild && SelectionActions.pendingCommands() > 0) {
			int left = SelectionActions.cancelPending();
			commandBuild = false;
			Editor.message(mc, Component.translatable("holoprint.message.build_stopped", left));
			return;
		}
		commandBuild = false;
		Hologram hologram = ray == null ? null : HologramRenderer.pick(Freecam.isActive() ? Freecam.eyePosition() : mc.player.getEyePosition(), ray, PICK_DISTANCE, null);
		if (hologram != null) {
			HologramRenderer.makeActive(hologram);
		} else {
			hologram = HologramRenderer.active();
		}
		if (hologram == null) {
			Editor.message(mc, Component.translatable("holoprint.message.build_none"));
			return;
		}
		if (!SelectionActions.hasCommandAccess(mc)) {
			Editor.message(mc, Component.translatable("holoprint.message.build_no_access"));
			return;
		}
		if (busy) {
			Editor.message(mc, Component.translatable("holoprint.message.build_busy"));
			return;
		}
		long now = System.currentTimeMillis();
		if (confirming != hologram || now > confirmUntil) {
			confirming = hologram;
			confirmUntil = now + CONFIRM_MS;
			Editor.message(mc, Component.translatable(withServer(mc) ? "holoprint.message.build_confirm" : "holoprint.message.build_confirm_commands",
				hologram.schematic().totalBlocks(), hologram.displayName(), EditorHud.name(EditorKeys.BUILD)));
			return;
		}
		confirming = null;
		build(mc, hologram);
	}

	/** True when the blocks can be written by the world's own server (a world of one's own) instead of being sent as commands. */
	private static boolean withServer(Minecraft mc) {
		return mc.getSingleplayerServer() != null && !forceCommands;
	}

	/** Builds {@code hologram} now, without asking. Returns false when it cannot be started. */
	public static boolean build(Minecraft mc, Hologram hologram) {
		return build(mc, hologram, null);
	}

	/**
	 * Builds {@code hologram} now, after emptying {@code clear} (the entities in it too) when that is not null. Returns false when it
	 * cannot be started.
	 */
	public static boolean build(Minecraft mc, Hologram hologram, Area clear) {
		if (mc.level == null || !SelectionActions.hasCommandAccess(mc) || busy || commandBuild && SelectionActions.pendingCommands() > 0) {
			return false;
		}
		if (withServer(mc)) {
			return buildDirect(mc, mc.getSingleplayerServer(), hologram, clear);
		}
		List<String> commands = commands(mc, hologram, clear);
		SelectionActions.queue(commands);
		commandBuild = true;
		Editor.message(mc, Component.translatable("holoprint.message.build_commands", commands.size(), Math.max(0, commands.size() - 9)));
		return true;
	}

	// --- in a world of one's own: written by the server ---

	private static boolean buildDirect(Minecraft mc, MinecraftServer server, Hologram hologram, Area clear) {
		ResourceKey<Level> dimension = mc.level.dimension();
		BlockPos origin = hologram.origin();
		int sizeX = hologram.sizeX(), sizeZ = hologram.sizeZ();
		int length = hologram.gridLength();
		long clearing = clear == null ? 0 : (long) clear.sizeX() * clear.sizeY() * clear.sizeZ();
		List<Object[]> blockEntities = new ArrayList<>();
		hologram.forEachBlockEntity((local, data) -> blockEntities.add(new Object[] {origin.offset(local), data}));
		List<CompoundTag> entities = hologram.entityTags(mc);
		busy = true;
		Editor.message(mc, Component.translatable("holoprint.message.building", length));
		long[] cursor = {0};
		long[] placed = {0};
		Runnable[] step = new Runnable[1];
		step[0] = () -> {
			ServerLevel level = server.getLevel(dimension);
			if (level == null) {
				busy = false;
				return;
			}
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			if (cursor[0] < clearing) {
				// First the area the hologram stands in for is emptied (and its entities removed, as they come again from the hologram).
				if (cursor[0] == 0) {
					AABB box = new AABB(clear.min().getX(), clear.min().getY(), clear.min().getZ(),
						clear.min().getX() + clear.sizeX(), clear.min().getY() + clear.sizeY(), clear.min().getZ() + clear.sizeZ());
					for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
						entity.discard();
					}
				}
				long end = Math.min(clearing, cursor[0] + BATCH);
				for (long i = cursor[0]; i < end; i++) {
					int x = (int) (i % clear.sizeX()), z = (int) ((i / clear.sizeX()) % clear.sizeZ()), y = (int) (i / ((long) clear.sizeX() * clear.sizeZ()));
					at.set(clear.min().getX() + x, clear.min().getY() + y, clear.min().getZ() + z);
					if (!level.getBlockState(at).isAir()) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
					}
				}
				cursor[0] = end;
				server.execute(step[0]);
				return;
			}
			long start = cursor[0] - clearing;
			long end = Math.min(length, start + BATCH);
			for (long i = start; i < end; i++) {
				BlockState state = hologram.gridState((int) i);
				if (state == null) {
					continue;
				}
				int x = (int) (i % sizeX), z = (int) ((i / sizeX) % sizeZ), y = (int) (i / ((long) sizeX * sizeZ));
				at.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
				if (level.setBlock(at, state, FLAGS)) {
					placed[0]++;
				}
			}
			cursor[0] = clearing + end;
			if (end < length) {
				server.execute(step[0]);
				return;
			}
			for (Object[] entry : blockEntities) {
				BlockPos pos = (BlockPos) entry[0];
				try {
					BlockEntity entity = BlockEntity.loadStatic(pos, level.getBlockState(pos), ((CompoundTag) entry[1]).copy(), level.registryAccess());
					if (entity != null) {
						level.setBlockEntity(entity);
					}
				} catch (RuntimeException ignored) {
					// A block entity this version cannot build is left as the empty one the block made.
				}
			}
			for (CompoundTag tag : entities) {
				try {
					Entity entity = EntityType.loadEntityRecursive(tag.copy(), level, new EntitySpawnRequest(EntitySpawnReason.LOAD, true), EntityProcessor.NOP);
					if (entity != null) {
						level.tryAddFreshEntityWithPassengers(entity);
					}
				} catch (RuntimeException ignored) {
					// An entity this version cannot build is left out.
				}
			}
			busy = false;
			mc.execute(() -> Editor.message(mc, Component.translatable("holoprint.message.built", placed[0])));
		};
		server.execute(step[0]);
		return true;
	}

	// --- on any other server: sent as commands ---

	/** The commands that empty {@code clear} (if any) and then build {@code hologram}, in an order that suits blocks that hang on others. */
	public static List<String> commands(Minecraft mc, Hologram hologram, Area clear) {
		List<String> out = new ArrayList<>();
		if (clear != null) {
			for (int x = 0; x < clear.sizeX(); x += 32) {
				for (int y = 0; y < clear.sizeY(); y += 32) {
					for (int z = 0; z < clear.sizeZ(); z += 32) {
						out.add("fill " + (clear.min().getX() + x) + " " + (clear.min().getY() + y) + " " + (clear.min().getZ() + z) + " "
							+ (clear.min().getX() + Math.min(x + 31, clear.sizeX() - 1)) + " " + (clear.min().getY() + Math.min(y + 31, clear.sizeY() - 1)) + " "
							+ (clear.min().getZ() + Math.min(z + 31, clear.sizeZ() - 1)) + " air");
					}
				}
			}
			AABB box = new AABB(clear.min().getX(), clear.min().getY(), clear.min().getZ(),
				clear.min().getX() + clear.sizeX(), clear.min().getY() + clear.sizeY(), clear.min().getZ() + clear.sizeZ());
			if (!mc.level.getEntities((Entity) null, box, e -> !(e instanceof Player)).isEmpty()) {
				out.add("kill @e[type=!player,x=" + clear.min().getX() + ",y=" + clear.min().getY() + ",z=" + clear.min().getZ()
					+ ",dx=" + (clear.sizeX() - 1) + ",dy=" + (clear.sizeY() - 1) + ",dz=" + (clear.sizeZ() - 1) + "]");
			}
		}
		BlockPos origin = hologram.origin();
		int sizeX = hologram.sizeX(), sizeY = hologram.sizeY(), sizeZ = hologram.sizeZ();
		// The blocks that carry data are written one by one afterwards; the rest are merged into boxes of identical blocks.
		BitSet done = new BitSet(hologram.gridLength());
		List<Object[]> withData = new ArrayList<>();
		hologram.forEachBlockEntity((local, data) -> {
			done.set((local.getY() * sizeZ + local.getZ()) * sizeX + local.getX());
			withData.add(new Object[] {local, data});
		});
		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					int index = (y * sizeZ + z) * sizeX + x;
					BlockState state = hologram.gridState(index);
					if (state == null) {
						done.set(index);
					}
					if (done.get(index)) {
						continue;
					}
					int nx = 1;
					while (x + nx < sizeX && nx < FILL_LIMIT && matches(hologram, done, index + nx, state)) {
						nx++;
					}
					int nz = 1;
					while (z + nz < sizeZ && (long) nx * (nz + 1) <= FILL_LIMIT && rowMatches(hologram, done, (y * sizeZ + z + nz) * sizeX + x, nx, state)) {
						nz++;
					}
					int ny = 1;
					while (y + ny < sizeY && (long) nx * nz * (ny + 1) <= FILL_LIMIT && slabMatches(hologram, done, y + ny, z, x, nx, nz, state)) {
						ny++;
					}
					for (int dy = 0; dy < ny; dy++) {
						for (int dz = 0; dz < nz; dz++) {
							done.set(((y + dy) * sizeZ + z + dz) * sizeX + x, ((y + dy) * sizeZ + z + dz) * sizeX + x + nx);
						}
					}
					String block = BlockStateParser.serialize(state);
					int x1 = origin.getX() + x, y1 = origin.getY() + y, z1 = origin.getZ() + z;
					if (nx * nz * ny == 1) {
						out.add("setblock " + x1 + " " + y1 + " " + z1 + " " + block);
					} else {
						out.add("fill " + x1 + " " + y1 + " " + z1 + " " + (x1 + nx - 1) + " " + (y1 + ny - 1) + " " + (z1 + nz - 1) + " " + block);
					}
				}
			}
		}
		for (Object[] entry : withData) {
			BlockPos local = (BlockPos) entry[0];
			BlockState state = hologram.gridState((local.getY() * sizeZ + local.getZ()) * sizeX + local.getX());
			if (state == null) {
				continue;
			}
			CompoundTag data = ((CompoundTag) entry[1]).copy();
			for (String key : new String[] {"x", "y", "z", "id"}) {
				data.remove(key);
			}
			String command = "setblock " + (origin.getX() + local.getX()) + " " + (origin.getY() + local.getY()) + " " + (origin.getZ() + local.getZ()) + " "
				+ BlockStateParser.serialize(state) + (data.isEmpty() ? "" : data.toString());
			if (command.length() > MAX_COMMAND) {
				// Too much data for one command: the block goes without it.
				command = "setblock " + (origin.getX() + local.getX()) + " " + (origin.getY() + local.getY()) + " " + (origin.getZ() + local.getZ()) + " " + BlockStateParser.serialize(state);
			}
			out.add(command);
		}
		for (CompoundTag tag : hologram.entityTags(mc)) {
			ListTag pos = tag.getListOrEmpty("Pos");
			String type = tag.getStringOr("id", "");
			if (pos.size() < 3 || type.isEmpty()) {
				continue;
			}
			tag.remove("id");
			tag.remove("Pos");
			String command = String.format(Locale.ROOT, "summon %s %.4f %.4f %.4f %s", type, pos.getDoubleOr(0, 0.0), pos.getDoubleOr(1, 0.0), pos.getDoubleOr(2, 0.0), tag);
			if (command.length() <= MAX_COMMAND) {
				out.add(command);
			}
		}
		return out;
	}

	private static boolean matches(Hologram hologram, BitSet done, int index, BlockState state) {
		return !done.get(index) && hologram.gridState(index) == state;
	}

	/** True when the {@code count} cells from {@code index} on are all free and hold {@code state}. */
	private static boolean rowMatches(Hologram hologram, BitSet done, int index, int count, BlockState state) {
		for (int i = 0; i < count; i++) {
			if (!matches(hologram, done, index + i, state)) {
				return false;
			}
		}
		return true;
	}

	/** True when layer {@code y}, from row {@code z} for {@code nz} rows and from column {@code x} for {@code nx} cells, is free and all {@code state}. */
	private static boolean slabMatches(Hologram hologram, BitSet done, int y, int z, int x, int nx, int nz, BlockState state) {
		for (int dz = 0; dz < nz; dz++) {
			if (!rowMatches(hologram, done, (y * hologram.sizeZ() + z + dz) * hologram.sizeX() + x, nx, state)) {
				return false;
			}
		}
		return true;
	}
}
