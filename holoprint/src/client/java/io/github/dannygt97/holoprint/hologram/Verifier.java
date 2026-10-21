package io.github.dannygt97.holoprint.hologram;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.dannygt97.holoprint.config.HoloprintConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Compares every hologram with the world, a little every tick: which blocks are already built (so the
 * hologram can leave them out), which are missing, which are another block or the same block turned
 * the wrong way, and how many of each kind of block the build still needs. Only chunks the client has
 * are looked at, and the work is spread over many ticks, so a big hologram costs a few milliseconds a tick.
 */
public final class Verifier {
	/** Cells looked at per tick, over all holograms. */
	private static final int BUDGET = 40_000;
	/** Ticks between the end of a check and the start of the next one. */
	private static final int RESCAN_TICKS = 40;
	/** The most positions kept for each kind of difference, which is what gets drawn. */
	private static final int MAX_MARKS = 20_000;

	/** One line of the material list. */
	public record Row(Object key, Component name, long needed, long built, long missing) {
		public long placed() {
			return built;
		}
	}

	/** The outcome of one complete check of one hologram. */
	public static final class Result {
		private final BlockPos origin;
		private final int turns;
		private final boolean mirrored;
		/** Cell numbers (see {@link Hologram#gridState}) that hold the asked block already. */
		public final BitSet built;
		/** World positions of blocks that are another block than the one asked for. */
		public volatile long[] wrong;
		/** World positions of the right block in the wrong state (turned the wrong way, wrong half...). */
		public volatile long[] wrongState;
		/** World positions of the right block, facing right, whose setting differs (repeater delay, comparator mode, note, inverted sensor). */
		public volatile long[] wrongSetting = new long[0];
		public volatile long wrongSettingCount;
		/** Blocks of the hologram above or below the height of the world: nothing can be built there, so they are left out of every count. */
		public volatile long outside;
		/** World positions of blocks where the hologram asks for nothing (only looked for when the option is on). */
		public final long[] extra;
		public final List<Row> rows;
		public final long needed, placed, missing, wrongCount, wrongStateCount, extraCount, unloaded;
		public final long gameTime;

		Result(BlockPos origin, int turns, boolean mirrored, BitSet built, long[] wrong, long[] wrongState, long[] extra, List<Row> rows,
			long needed, long placed, long missing, long wrongCount, long wrongStateCount, long extraCount, long unloaded, long gameTime) {
			this.origin = origin;
			this.turns = turns;
			this.mirrored = mirrored;
			this.built = built;
			this.wrong = wrong;
			this.wrongState = wrongState;
			this.extra = extra;
			this.rows = rows;
			this.needed = needed;
			this.placed = placed;
			this.missing = missing;
			this.wrongCount = wrongCount;
			this.wrongStateCount = wrongStateCount;
			this.extraCount = extraCount;
			this.unloaded = unloaded;
			this.gameTime = gameTime;
		}

		/** Marks one cell as it is now, at once and without waiting for the next full check: 0 nothing to mark, 1 another block, 2 the right block in the wrong state, 3 the right block with a different setting. */
		void mark(long position, int kind) {
			wrong = with(wrong, position, kind == 1);
			wrongState = with(wrongState, position, kind == 2);
			wrongSetting = with(wrongSetting, position, kind == 3);
		}

		private static long[] with(long[] list, long position, boolean present) {
			boolean has = false;
			for (long value : list) {
				if (value == position) {
					has = true;
					break;
				}
			}
			if (has == present) {
				return list;
			}
			LongArrayList out = new LongArrayList(list.length + 1);
			for (long value : list) {
				if (value != position) {
					out.add(value);
				}
			}
			if (present && out.size() < MAX_MARKS) {
				out.add(position);
			}
			return out.toLongArray();
		}

		/** Something to build, and none of it missing, wrong or in a part the client has not got. */
		public boolean isComplete() {
			return needed > 0 && missing == 0 && wrongCount == 0 && wrongStateCount == 0 && unloaded == 0;
		}

		/** True while the hologram is still where and as it was when this was worked out. */
		public boolean matches(BlockPos origin, int turns, boolean mirrored) {
			return this.origin.equals(origin) && this.turns == turns && this.mirrored == mirrored;
		}
	}

	/** A check in progress. */
	private static final class Job {
		final BlockPos origin;
		final int turns;
		final boolean mirrored;
		final boolean withExtra;
		/** The cells to look at, in order, when only the blocks matter (the whole box is walked when empty cells are looked at too). */
		final int[] cells;
		int cursor;
		long startedAt;
		boolean finished;
		long finishedAt = -RESCAN_TICKS;
		final BitSet built;
		final LongArrayList wrong = new LongArrayList();
		final LongArrayList wrongState = new LongArrayList();
		final LongArrayList wrongSetting = new LongArrayList();
		long wrongSettingCount;
		long outside;
		final LongArrayList extra = new LongArrayList();
		final Map<Object, long[]> counts = new LinkedHashMap<>();
		long needed, placed, missing, wrongCount, wrongStateCount, extraCount, unloaded;
		int lastChunkX = Integer.MIN_VALUE, lastChunkZ = Integer.MIN_VALUE;
		boolean lastLoaded;

		Job(Hologram hologram, boolean withExtra) {
			this.origin = hologram.origin();
			this.turns = hologram.quarterTurns();
			this.mirrored = hologram.mirrored();
			this.withExtra = withExtra;
			this.cells = withExtra ? null : hologram.solidCells();
			this.built = new BitSet(hologram.gridLength());
		}

		boolean sameAs(Hologram hologram, boolean withExtra) {
			return origin.equals(hologram.origin()) && turns == hologram.quarterTurns() && mirrored == hologram.mirrored() && this.withExtra == withExtra;
		}
	}

	/** Holograms whose world changed since their last check started, and when (game time): they are checked again at once. */
	private static final Map<Hologram, Long> DIRTY = new IdentityHashMap<>();
	/** Ticks a change waits before the new check, so a burst of changes is looked at once. */
	private static final int DIRTY_DELAY = 2;
	private static final Map<Hologram, Job> JOBS = new IdentityHashMap<>();
	/** Holograms asked for by a window, and the game time until which they are checked. */
	private static final Map<Hologram, Long> FORCED = new IdentityHashMap<>();
	private static int rotation;

	private Verifier() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Verifier::tick);
	}

	/** Asks for {@code hologram} to be checked for the next few seconds, even when no option needs it (the material list is open). */
	public static void request(Hologram hologram) {
		Minecraft mc = Minecraft.getInstance();
		FORCED.put(hologram, (mc.level == null ? 0 : mc.level.getGameTime()) + 100);
	}

	/** How far (percent) the check that is going on for {@code hologram} has got, for the window that waits for it. */
	public static int progress(Hologram hologram) {
		Job job = JOBS.get(hologram);
		if (job == null) {
			return 0;
		}
		long length = job.cells == null ? hologram.gridLength() : job.cells.length;
		return job.finished ? 100 : (int) Math.min(99, job.cursor * 100L / Math.max(1, length));
	}

	/** Forgets the last result of a hologram, so the next check starts at once. */
	public static void restart(Hologram hologram) {
		JOBS.remove(hologram);
	}

	/**
	 * A block of the client's world changed (placed, broken, turned, opened...). The cell is looked at straight away, so what the
	 * hologram hides and what it outlines follows the world without delay, and the hologram is checked again in full within a
	 * couple of ticks to bring the counts and the material list up to date.
	 */
	public static void blockChanged(BlockPos pos, BlockState state) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		for (Hologram hologram : HologramRenderer.all()) {
			int x = pos.getX() - hologram.origin().getX(), y = pos.getY() - hologram.origin().getY(), z = pos.getZ() - hologram.origin().getZ();
			if (x < 0 || y < 0 || z < 0 || x >= hologram.sizeX() || y >= hologram.sizeY() || z >= hologram.sizeZ()) {
				continue;
			}
			if (!JOBS.containsKey(hologram) && hologram.verification() == null) {
				continue;
			}
			DIRTY.put(hologram, mc.level.getGameTime());
			Result result = hologram.verification();
			BlockState wanted = hologram.gridState((y * hologram.sizeZ() + z) * hologram.sizeX() + x);
			if (result == null || wanted == null || !result.matches(hologram.origin(), hologram.quarterTurns(), hologram.mirrored())) {
				continue;
			}
			int index = (y * hologram.sizeZ() + z) * hologram.sizeX() + x;
			boolean built = isBuilt(wanted, state);
			if (built != result.built.get(index)) {
			result.built.set(index, built);
			hologram.builtChanged(index);
			}
			int kind = 0;
			if (built) {
				if (state.getBlock() == wanted.getBlock() && (!sameKey(wanted, state) || openDiffers(wanted, state))) {
					kind = 2;
				} else if (settingClicks(wanted, state) > 0) {
					kind = 3;
				}
			} else if (!state.isAir() && !state.canBeReplaced()) {
				kind = 1;
			}
			result.mark(pos.asLong(), kind);
		}
	}

	/** True when {@code world} is the block {@code wanted} asks for (or the same fluid). */
	public static boolean isBuilt(BlockState wanted, BlockState world) {
		return world.getBlock() == wanted.getBlock()
			|| (wanted.getBlock() instanceof LiquidBlock && world.getFluidState().getType().isSame(wanted.getFluidState().getType()) && !world.getFluidState().isEmpty())
			// the bamboo item gives a sapling where there is no bamboo yet, which grows into the stalk by itself
			|| (wanted.is(net.minecraft.world.level.block.Blocks.BAMBOO) && world.is(net.minecraft.world.level.block.Blocks.BAMBOO_SAPLING));
	}

	/** True for the properties that placing a block decides and that must match: which way it faces, which half it is, how it is mounted. */
	public static boolean decisive(BlockState wanted, Property<?> property) {
		return switch (property.getName()) {
			case "facing", "half", "axis", "face", "orientation" -> true;
			case "type" -> wanted.getBlock() instanceof net.minecraft.world.level.block.SlabBlock;
			default -> false;
		};
	}

	/**
	 * How many use clicks it takes to bring the setting of {@code world} to the one {@code wanted} has: the delay of a repeater (1 to 4,
	 * a click adds one), the mode of a comparator, the note of a note block, a daylight detector's inverted switch. Zero when it is the
	 * same already or the block has no such setting.
	 */
	public static int settingClicks(BlockState wanted, BlockState world) {
		Block block = wanted.getBlock();
		if (block != world.getBlock()) {
			return 0;
		}
		if (block instanceof net.minecraft.world.level.block.RepeaterBlock) {
			return Math.floorMod(wanted.getValue(BlockStateProperties.DELAY) - world.getValue(BlockStateProperties.DELAY), 4);
		}
		if (block instanceof net.minecraft.world.level.block.ComparatorBlock) {
			return wanted.getValue(BlockStateProperties.MODE_COMPARATOR) != world.getValue(BlockStateProperties.MODE_COMPARATOR) ? 1 : 0;
		}
		if (block instanceof net.minecraft.world.level.block.NoteBlock) {
			return Math.floorMod(wanted.getValue(BlockStateProperties.NOTE) - world.getValue(BlockStateProperties.NOTE), 25);
		}
		if (block instanceof net.minecraft.world.level.block.DaylightDetectorBlock) {
			return wanted.getValue(BlockStateProperties.INVERTED) != world.getValue(BlockStateProperties.INVERTED) ? 1 : 0;
		}
		return 0;
	}

	/** True for the blocks whose setting is changed by using them (see {@link #settingClicks}). */
	public static boolean adjustable(BlockState state) {
		Block block = state.getBlock();
		return block instanceof net.minecraft.world.level.block.RepeaterBlock || block instanceof net.minecraft.world.level.block.ComparatorBlock
			|| block instanceof net.minecraft.world.level.block.NoteBlock || block instanceof net.minecraft.world.level.block.DaylightDetectorBlock;
	}

	/** A door, trapdoor or gate that is open where the schematic has it closed, or the other way round. */
	private static boolean openDiffers(BlockState wanted, BlockState world) {
		Block block = wanted.getBlock();
		return (block instanceof net.minecraft.world.level.block.DoorBlock || block instanceof net.minecraft.world.level.block.TrapDoorBlock || block instanceof net.minecraft.world.level.block.FenceGateBlock)
			&& wanted.hasProperty(BlockStateProperties.OPEN) && world.hasProperty(BlockStateProperties.OPEN)
			&& wanted.getValue(BlockStateProperties.OPEN) != world.getValue(BlockStateProperties.OPEN);
	}

	private static boolean sameKey(BlockState wanted, BlockState world) {
		for (Property<?> property : wanted.getProperties()) {
			if (decisive(wanted, property) && (!world.hasProperty(property) || !world.getValue(property).equals(wanted.getValue(property)))) {
				return false;
			}
		}
		return true;
	}

	/** How many items a block takes to place (a double slab is two, a pile of snow its layers; the top half of a door is none). */
	public static int itemsNeeded(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof LiquidBlock || block.asItem() == Items.AIR || !(block.asItem() instanceof BlockItem)) {
			return 0;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return 0;
		}
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
			return 0;
		}
		if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
			return 2;
		}
		if (state.hasProperty(BlockStateProperties.LAYERS)) {
			return state.getValue(BlockStateProperties.LAYERS);
		}
		if (state.hasProperty(BlockStateProperties.CANDLES)) {
			return state.getValue(BlockStateProperties.CANDLES);
		}
		if (state.hasProperty(BlockStateProperties.PICKLES)) {
			return state.getValue(BlockStateProperties.PICKLES);
		}
		if (state.hasProperty(BlockStateProperties.EGGS)) {
			return state.getValue(BlockStateProperties.EGGS);
		}
		return 1;
	}

	private static Object keyOf(BlockState state) {
		Item item = state.getBlock().asItem();
		return item == Items.AIR ? state.getBlock() : item;
	}

	private static Component nameOf(Object key) {
		return key instanceof Item item ? new ItemStack(item).getHoverName() : ((Block) key).getName();
	}

	private static boolean wants(Hologram hologram, long now) {
		if (!hologram.visible() || !hologram.enabled() || hologram.sizeX() == 0) {
			return false;
		}
		HoloprintConfig config = HoloprintConfig.get();
		return config.hideBuilt || config.showDifferences || now < FORCED.getOrDefault(hologram, 0L);
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			JOBS.clear();
			return;
		}
		long now = mc.level.getGameTime();
		List<Hologram> all = HologramRenderer.all();
		JOBS.keySet().removeIf(h -> !all.contains(h));
		if (all.isEmpty()) {
			return;
		}
		boolean withExtra = HoloprintConfig.get().showExtra;
		// A hologram that has no result at all yet (the material list is waiting for it) is looked at four times as fast: nobody sees a frame lost to it,
		// but everybody sees the wait.
		boolean cold = false;
		for (Hologram hologram : all) {
			cold |= wants(hologram, now) && hologram.verification() == null;
		}
		int budget = cold ? BUDGET * 4 : BUDGET;
		for (int n = 0; n < all.size() && budget > 0; n++) {
			Hologram hologram = all.get((rotation + n) % all.size());
			if (!wants(hologram, now)) {
				continue;
			}
			Job job = JOBS.get(hologram);
			Long changedAt = DIRTY.get(hologram);
			// A pass that is going on is never thrown away for a change (a build with observers or pistons in it changes something every few ticks, and the pass
			// would start over each time and never end): it finishes, and then the change is looked at in a pass of its own.
			boolean dirty = changedAt != null && now - changedAt >= DIRTY_DELAY && (job == null || job.finished);
			if (!dirty && job != null && job.finished && now - job.finishedAt < RESCAN_TICKS && job.sameAs(hologram, withExtra)) {
				continue;
			}
			if (dirty) {
				DIRTY.remove(hologram);
				job = null;
			}
			if (job == null || job.finished || !job.sameAs(hologram, withExtra)) {
				job = new Job(hologram, withExtra);
				job.startedAt = now;
				JOBS.put(hologram, job);
			}
			budget -= step(mc, hologram, job, budget, now);
		}
		rotation++;
	}

	/** Looks at up to {@code budget} more cells; returns how many were looked at. */
	private static int step(Minecraft mc, Hologram hologram, Job job, int budget, long now) {
		int sizeX = hologram.sizeX(), sizeZ = hologram.sizeZ();
		int[] cells = job.cells;
		int length = cells == null ? hologram.gridLength() : cells.length;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int done = 0;
		while (job.cursor < length && done < budget) {
			int i = cells == null ? job.cursor++ : cells[job.cursor++];
			done++;
			BlockState wanted = hologram.gridState(i);
			if (wanted == null && !job.withExtra) {
				continue;
			}
			int x = i % sizeX;
			int z = (i / sizeX) % sizeZ;
			int y = i / (sizeX * sizeZ);
			pos.set(job.origin.getX() + x, job.origin.getY() + y, job.origin.getZ() + z);
			int chunkX = pos.getX() >> 4, chunkZ = pos.getZ() >> 4;
			if (chunkX != job.lastChunkX || chunkZ != job.lastChunkZ) {
				job.lastChunkX = chunkX;
				job.lastChunkZ = chunkZ;
				// hasChunk says yes for any chunk inside the view window, even one the server never sent: the block would read as void air
				job.lastLoaded = mc.level.getChunk(chunkX, chunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null;
			}
			if (wanted == null) {
				if (job.lastLoaded) {
					BlockState world = mc.level.getBlockState(pos);
					if (!world.isAir() && !world.canBeReplaced()) {
						job.extraCount++;
						if (job.extra.size() < MAX_MARKS) {
							job.extra.add(pos.asLong());
						}
					}
				}
				continue;
			}
			if (pos.getY() < mc.level.getMinY() || pos.getY() >= mc.level.getMaxY()) {
				// out of the world: it can never be built, and it must not keep the placement from being finished (nor keep its outline)
				job.outside++;
				job.built.set(i);
				continue;
			}
			int items = itemsNeeded(wanted);
			long[] count = items == 0 ? null : job.counts.computeIfAbsent(keyOf(wanted), k -> new long[3]);
			job.needed += items;
			if (count != null) {
				count[0] += items;
			}
			if (!job.lastLoaded) {
				if (hologram.assumedBuilt()) {
					// the Build wrote it; the client just does not have that part of the world (the free camera sees farther than the player's chunks)
					job.built.set(i);
					job.placed += items;
					if (count != null) {
						count[1] += items;
					}
				} else {
					job.unloaded++;
				}
				continue;
			}
			BlockState world = mc.level.getBlockState(pos);
			if (isBuilt(wanted, world)) {
				job.built.set(i);
				job.placed += items;
				if (count != null) {
					count[1] += items;
				}
				if (world.getBlock() == wanted.getBlock() && (!sameKey(wanted, world) || openDiffers(wanted, world))) {
					job.wrongStateCount++;
					if (job.wrongState.size() < MAX_MARKS) {
						job.wrongState.add(pos.asLong());
					}
				} else if (settingClicks(wanted, world) > 0) {
					job.wrongSettingCount++;
					if (job.wrongSetting.size() < MAX_MARKS) {
						job.wrongSetting.add(pos.asLong());
					}
				}
			} else {
				job.missing += items;
				if (count != null) {
					count[2] += items;
				}
				if (!world.isAir() && !world.canBeReplaced()) {
					job.wrongCount++;
					if (job.wrong.size() < MAX_MARKS) {
						job.wrong.add(pos.asLong());
					}
				}
			}
		}
		if (job.cursor >= length) {
			job.finished = true;
			job.finishedAt = now;
			List<Row> rows = new ArrayList<>();
			job.counts.forEach((key, c) -> rows.add(new Row(key, nameOf(key), c[0], c[1], c[2])));
			rows.sort((a, b) -> a.missing() != b.missing() ? Long.compare(b.missing(), a.missing()) : a.name().getString().compareToIgnoreCase(b.name().getString()));
			Result made = new Result(job.origin, job.turns, job.mirrored, job.built, job.wrong.toLongArray(), job.wrongState.toLongArray(), job.extra.toLongArray(),
				rows, job.needed, job.placed, job.missing, job.wrongCount, job.wrongStateCount, job.extraCount, job.unloaded, now);
			made.wrongSetting = job.wrongSetting.toLongArray();
			made.wrongSettingCount = job.wrongSettingCount;
			made.outside = job.outside;
			Result before = hologram.verification();
			boolean wasComplete = before != null && before.matches(job.origin, job.turns, job.mirrored) && before.isComplete();
			hologram.verification(made);
			// A placement opened again that no longer matches (it was moved) goes back to being checked as usual.
			// (a move that is still being made, and may be cancelled, does not count; neither does a check of where it used to be)
			if (hologram.reopened() && !hologram.moving() && !made.isComplete() && made.matches(hologram.origin(), hologram.quarterTurns(), hologram.mirrored())) {
				hologram.reopened(false);
			}
			// It has just been finished (the last block went in, or a Build ended): it says so, and stops being the one Move, R and M work on.
			if ((before != null || hologram.assumedBuilt()) && !wasComplete && made.isComplete() && !hologram.reopened() && !hologram.markedComplete()) {
				if (mc.player != null) {
					Component finished = Component.translatable("holoprint.message.placement_complete", hologram.displayName()).withStyle(net.minecraft.ChatFormatting.GREEN);
					mc.player.sendSystemMessage(finished);
					mc.gui.hud.setOverlayMessage(finished, false);
				}
				if (hologram == HologramRenderer.active() && !hologram.locked()) {
					HologramRenderer.deselect(hologram);
					io.github.dannygt97.holoprint.placement.Editor.activePlacementChanged(mc);
				}
			} else if (wasComplete && !made.isComplete() && !hologram.markedComplete() && !hologram.reopened() && made.matches(hologram.origin(), hologram.quarterTurns(), hologram.mirrored())
				&& made.missing + made.wrongCount + made.wrongStateCount > 0 && mc.player != null) {
				// It was finished and a block has been taken away or changed: it is not finished any more, and it says so (it is drawn again, only what is wrong).
				Component open = Component.translatable("holoprint.message.placement_open", hologram.displayName(), made.missing + made.wrongCount + made.wrongStateCount).withStyle(net.minecraft.ChatFormatting.YELLOW);
				mc.player.sendSystemMessage(open);
				mc.gui.hud.setOverlayMessage(open, false);
			}
		}
		return done;
	}
}
