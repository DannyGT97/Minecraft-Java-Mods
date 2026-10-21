package io.github.dannygt97.holoprint.hologram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.dannygt97.holoprint.schematic.Region;
import io.github.dannygt97.holoprint.schematic.Schematic;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A schematic placed in the world. The blocks are turned and mirrored once into a box whose minimum
 * corner is the local origin, so moving the hologram only changes {@link #origin()}; turning or
 * mirroring it rebuilds the list. Only the faces that can be seen are kept (a face against another
 * opaque block of the hologram is hidden, so it shows its surface and not its inside).
 *
 * The rebuild works on one flat array of palette numbers instead of a map of positions, so even a
 * schematic of millions of blocks needs little memory and little time.
 */
public final class Hologram {
	/** Largest box, in blocks, that can be turned into a hologram. */
	private static final long MAX_VOLUME = 400_000_000L;

	/** Kinds of fluid a hologram block can be: water or lava. */
	public static final int NO_FLUID = 0, WATER = 1, LAVA = 2;
	/** Where a fluid block flows: nothing, straight down, or one of eight horizontal directions (FLOW_FIRST + 0 to 7, counter-clockwise from +x, in steps of 45 degrees on the x-z plane). */
	public static final int NO_FLOW = 0, FALLING = 1, FLOW_FIRST = 2;

	/**
	 * One block of the hologram: where it is relative to the origin, what it is, which of its six faces are exposed and which fluid it is (0 for none).
	 * For a fluid, {@code heights} holds the height of its surface at the four corners (index {@code 2 * z + x} of the corner, as a fraction of a block), null otherwise.
	 * A block that holds water (a waterlogged slab, kelp, seagrass) is both: {@code waterlogged} is true, {@code visibleFaces} are the faces of its water
	 * and {@code modelFaces} those of its own model, which only an opaque neighbour hides.
	 */
	public record Entry(BlockPos local, BlockState state, int visibleFaces, int fluid, int flow, float[] heights, boolean waterlogged, int modelFaces) {
		public boolean faceVisible(Direction direction) {
			return (visibleFaces & (1 << direction.get3DDataValue())) != 0;
		}

		/** Whether the block's own model shows its face on that side (the same as {@link #faceVisible} for anything that is not waterlogged). */
		public boolean modelFaceVisible(Direction direction) {
			return (modelFaces & (1 << direction.get3DDataValue())) != 0;
		}
	}

	/** A 16 x 16 x 16 cell of the hologram, so that whole cells can be skipped when they are out of sight. */
	public static final class Section {
		public final List<Entry> entries = new ArrayList<>();
		/** The cached quads of this cell, filled by the renderer when it is first drawn. */
		volatile SectionMesh mesh;
		public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		public int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

		void add(Entry entry) {
			entries.add(entry);
			BlockPos p = entry.local();
			minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX() + 1);
			minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY() + 1);
			minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ() + 1);
		}
	}

	private final Schematic schematic;
	private BlockPos origin = BlockPos.ZERO;
	private int quarterTurns;
	private boolean mirrored;
	private List<Entry> entries = List.of();
	private List<Section> sections = List.of();
	/** What the user can change about a placement from the placements window. */
	private String label = "";
	private boolean visible = true;
	private boolean enabled = true;
	private boolean locked;
	/** The user said this placement is finished, whatever the check of the world finds. */
	private boolean markedComplete;
	/** A finished placement the user opened again to move it: it is drawn whole, built blocks included, and is not taken as complete until it is marked again. */
	private boolean reopened;
	/** Where it stood and how it was turned when the Build of it ended, or null: what the client has not got (chunks out of reach of the player, which the free camera can look at) is taken as built. */
	private volatile Object[] builtBy;

	/** Called when a Build of this placement ended: the blocks it wrote are in the world even where the client has not got them yet. */
	public void markBuilt() {
		builtBy = new Object[] {origin, quarterTurns, mirrored};
		builtEpoch++;
	}

	/** Where and how turned the placement was when a Build of it ended, or null (saved with the placement). */
	public Object[] builtState() {
		return builtBy;
	}

	/** Puts back what {@link #builtState} gave, when the placements are restored. */
	public void restoreBuilt(BlockPos at, int turns, boolean mirror) {
		builtBy = new Object[] {at, turns & 3, mirror};
	}

	/** True while the placement is where it was when it was built (moving or turning it, or opening it again, takes the assumption back). */
	public boolean assumedBuilt() {
		Object[] by = builtBy;
		return by != null && !reopened && by[0].equals(origin) && (int) by[1] == quarterTurns && (boolean) by[2] == mirrored;
	}

	/** The editor is carrying it right now (a move not yet confirmed): where it is does not count yet. */
	private volatile boolean moving;
	private int sizeX, sizeY, sizeZ;
	/** Local positions of the exposed blocks, for finding out what the pointer is over. Built on first use. */
	private LongOpenHashSet solid;
	/** Every block of the hologram, hidden ones too: a number per cell, 0 for empty, else an index into {@link #gridStates}. */
	private int[] grid = new int[0];
	/** The numbers of the cells that hold a block, in ascending order: a huge box is mostly air, and the checks and the build walk only these. */
	private int[] solidCells = new int[0];
	private List<BlockState> gridStates = List.of();
	/** Whether the entities of the schematic are drawn for this placement (on top of the global option). */
	private boolean showEntities = true;
	/** The schematic's entities in this hologram's own coordinates, ready to be created. */
	private List<GhostEntities.Definition> entityDefinitions = List.of();
	/** The entities created from {@link #entityDefinitions}; made on first use on the game thread, and again after a turn. */
	private volatile List<GhostEntities.Ghost> ghosts;
	/** The chests, signs and other block-entity blocks of the schematic, in this hologram's own coordinates. */
	private List<GhostBlockEntities.Definition> blockEntityDefinitions = List.of();
	/** The block entities created from them, and the origin they were created for (they stand at real positions). */
	private List<GhostBlockEntities.Ghost> blockGhosts;
	private BlockPos blockGhostsAt;

	public Hologram(Schematic schematic, BlockPos origin) {
		this(schematic, origin, 0, false);
	}

	private Hologram(Schematic schematic, BlockPos origin, int turns, boolean mirror) {
		this.schematic = schematic;
		this.origin = origin;
		this.quarterTurns = turns & 3;
		this.mirrored = mirror;
		rebuild();
	}

	/** A hologram already placed and turned, for putting a saved placement back (the heavy work can then be done off the game thread). */
	public static Hologram placed(Schematic schematic, BlockPos origin, int quarterTurns, boolean mirrored) {
		return new Hologram(schematic, origin, quarterTurns, mirrored);
	}

	public Schematic schematic() { return schematic; }
	/** World position of the hologram's minimum corner. */
	public BlockPos origin() { return origin; }
	public List<Entry> entries() { return entries; }
	public List<Section> sections() { return sections; }
	public String label() { return label; }
	public void label(String value) { this.label = value == null ? "" : value; }
	public boolean visible() { return visible; }
	public void visible(boolean value) { this.visible = value; }
	public boolean enabled() { return enabled; }
	public void enabled(boolean value) { this.enabled = value; }
	public boolean markedComplete() { return markedComplete; }
	public boolean reopened() { return reopened; }
	public boolean moving() { return moving; }
	public void moving(boolean value) { this.moving = value; }

	public void reopened(boolean value) {
		if (reopened != value) {
			reopened = value;
			// what is hidden for being built changes, so every cell of the view is made again
			builtEpoch++;
		}
	}
	public void markedComplete(boolean value) { this.markedComplete = value; }
	public boolean locked() { return locked; }
	public void locked(boolean value) { this.locked = value; }
	public boolean showEntities() { return showEntities; }
	public void showEntities(boolean value) { this.showEntities = value; }
	/** True when the schematic has entities to show. */
	public boolean hasEntities() { return !entityDefinitions.isEmpty(); }
	public int entityCount() { return entityDefinitions.size(); }

	/** The frozen entities of this hologram, created the first time they are asked for. */
	List<GhostEntities.Ghost> ghosts(net.minecraft.client.Minecraft mc) {
		List<GhostEntities.Ghost> current = ghosts;
		if (current == null) {
			current = GhostEntities.create(mc, entityDefinitions, quarterTurns, mirrored);
			ghosts = current;
		}
		return current;
	}

	/**
	 * The entities of the schematic as saved data ready to be made real at this hologram's place: turned and mirrored like the blocks,
	 * with their {@code Pos} in the world and without the identity they had in the world they were copied from.
	 */
	public List<CompoundTag> entityTags(net.minecraft.client.Minecraft mc) {
		List<CompoundTag> out = new ArrayList<>();
		if (mc.level == null || entityDefinitions.isEmpty()) {
			return out;
		}
		for (GhostEntities.Ghost ghost : GhostEntities.create(mc, entityDefinitions, quarterTurns, mirrored, false)) {
			net.minecraft.world.entity.Entity entity = ghost.entity();
			entity.setPos(origin.getX() + ghost.x(), origin.getY() + ghost.y(), origin.getZ() + ghost.z());
			net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
				net.minecraft.util.ProblemReporter.DISCARDING, mc.level.registryAccess());
			if (!entity.save(output)) {
				continue;
			}
			CompoundTag tag = output.buildResult();
			tag.remove("UUID");
			out.add(tag);
		}
		return out;
	}

	/** True when the schematic has a chest, barrel, furnace or other container to show the contents of. */
	public boolean hasContainers(net.minecraft.client.Minecraft mc) {
		if (blockEntityDefinitions.isEmpty()) {
			return false;
		}
		for (GhostBlockEntities.Ghost ghost : blockGhosts(mc)) {
			if (ghost.entity() instanceof net.minecraft.world.Container) {
				return true;
			}
		}
		return false;
	}

	/**
	 * What the container this hologram has at world position {@code world} holds, in slot order without the empty slots; null when
	 * there is no container there, and empty when it is empty.
	 */
	public List<net.minecraft.world.item.ItemStack> contentsAt(net.minecraft.client.Minecraft mc, BlockPos world) {
		if (blockEntityDefinitions.isEmpty() || !containsBox(world)) {
			return null;
		}
		BlockPos local = world.subtract(origin);
		for (GhostBlockEntities.Ghost ghost : blockGhosts(mc)) {
			if (ghost.local().equals(local) && ghost.entity() instanceof net.minecraft.world.Container container) {
				List<net.minecraft.world.item.ItemStack> items = new ArrayList<>();
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					net.minecraft.world.item.ItemStack stack = container.getItem(slot);
					if (!stack.isEmpty()) {
						items.add(stack);
					}
				}
				return items;
			}
		}
		return null;
	}

	/** Calls {@code action} for each chest, sign or similar block of the schematic: its cell in this hologram's own coordinates and its saved data. */
	public void forEachBlockEntity(java.util.function.BiConsumer<BlockPos, net.minecraft.nbt.CompoundTag> action) {
		for (GhostBlockEntities.Definition definition : blockEntityDefinitions) {
			action.accept(definition.local(), definition.data());
		}
	}

	/** The sign this hologram has at world position {@code world}, with the text it was saved with, or null when there is none. */
	public net.minecraft.world.level.block.entity.SignBlockEntity signAt(net.minecraft.client.Minecraft mc, BlockPos world) {
		if (blockEntityDefinitions.isEmpty() || !containsBox(world)) {
			return null;
		}
		for (GhostBlockEntities.Ghost ghost : blockGhosts(mc)) {
			if (ghost.entity() instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign && origin.offset(ghost.local()).equals(world)) {
				return sign;
			}
		}
		return null;
	}

	private boolean containsBox(BlockPos world) {
		int x = world.getX() - origin.getX(), y = world.getY() - origin.getY(), z = world.getZ() - origin.getZ();
		return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
	}

	/** True when the schematic has chests, signs or similar blocks to show. */
	public boolean hasBlockEntities() { return !blockEntityDefinitions.isEmpty(); }

	/** The block entities of this hologram at its current place, created the first time they are asked for and again after it moves. */
	List<GhostBlockEntities.Ghost> blockGhosts(net.minecraft.client.Minecraft mc) {
		List<GhostBlockEntities.Ghost> current = blockGhosts;
		if (current == null || !origin.equals(blockGhostsAt)) {
			current = GhostBlockEntities.create(mc, blockEntityDefinitions, origin);
			blockGhosts = current;
			blockGhostsAt = origin;
		}
		return current;
	}

	/** The name to show for this placement. */
	public String displayName() {
		if (!label.isBlank()) {
			return label;
		}
		String name = schematic.name();
		return name == null ? "" : name;
	}

	public int sizeX() { return sizeX; }
	public int sizeY() { return sizeY; }
	public int sizeZ() { return sizeZ; }
	public int quarterTurns() { return quarterTurns; }
	public boolean mirrored() { return mirrored; }

	/** A second hologram of the same schematic, in the same place and orientation. */
	public Hologram copy() {
		Hologram copy = new Hologram(schematic, origin, quarterTurns, mirrored);
		copy.showEntities = showEntities;
		return copy;
	}

	/** True when {@code world} is a block of this hologram that has an exposed face. */
	public boolean containsWorld(BlockPos world) {
		LongOpenHashSet set = solid;
		if (set == null) {
			set = new LongOpenHashSet(entries.size() * 2);
			for (Entry entry : entries) {
				set.add(entry.local().asLong());
			}
			solid = set;
		}
		return set.contains(world.subtract(origin).asLong());
	}

	/** The block this hologram asks for at a world position (turned and mirrored like the hologram), or null for air and for outside it. */
	public BlockState stateAt(BlockPos world) {
		int x = world.getX() - origin.getX(), y = world.getY() - origin.getY(), z = world.getZ() - origin.getZ();
		if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
			return null;
		}
		if (!LayerView.shows(y)) {
			return null;
		}
		int number = grid[(y * sizeZ + z) * sizeX + x];
		return number == 0 ? null : gridStates.get(number);
	}

	/** Number of cells of the hologram's box. */
	/** The cells of the box that hold a block, in ascending order (see {@link #gridState}). */
	public int[] solidCells() {
		return solidCells;
	}

	public int gridLength() {
		return grid.length;
	}

	/** The block asked for in cell {@code index} of the box (x runs fastest, then z, then y), or null for air. */
	public BlockState gridState(int index) {
		int number = grid[index];
		return number == 0 ? null : gridStates.get(number);
	}

	/** What the last check of the world found out about this hologram; null before the first one. */
	private volatile Verifier.Result verification;

	public Verifier.Result verification() {
		return verification;
	}

	void verification(Verifier.Result result) {
	Verifier.Result old = this.verification;
	this.verification = result;
	// The cells whose built state changed (all of them when there was nothing to compare with) are noted, so the cells of the view that hold them are made again.
	if (old == null || result == null || !old.matches(origin, quarterTurns, mirrored) || old.built == result.built) {
	builtEpoch++;
	} else {
	java.util.BitSet changed = (java.util.BitSet) old.built.clone();
	changed.xor(result.built);
	for (int i = changed.nextSetBit(0); i >= 0; i = changed.nextSetBit(i + 1)) {
	builtChanged(i);
	}
	}
	}

	// --- which cells of the view are out of date because blocks got built or removed ---

	/** How many 16-block cells the box has along each axis, and a number per cell that goes up whenever the built state of one of its blocks changes. */
	private int stampsX, stampsY, stampsZ;
	private int[] builtStamps = new int[0];
	private volatile int builtEpoch;

	/** Called when the built state of the block in cell {@code index} of the box changed. */
	void builtChanged(int index) {
	if (builtStamps.length == 0) {
	return;
	}
	int x = index % sizeX, z = (index / sizeX) % sizeZ, y = index / (sizeX * sizeZ);
	builtStamps[stampKey(x, y, z)]++;
	}

	/** The number of the 16-block cell holding the block at (x, y, z) of the box. */
	int stampKey(int x, int y, int z) {
	return ((y >> 4) * stampsZ + (z >> 4)) * stampsX + (x >> 4);
	}

	/** Changes whenever the built state of any block of that cell changes (see {@link #stampKey}). */
	int builtStamp(int key) {
	return key >= 0 && key < builtStamps.length ? builtStamps[key] + 7919 * builtEpoch : builtEpoch;
	}

	/** The cells that are already built, as of the last check, or null when there is no check that still applies. */
	public java.util.BitSet builtCells() {
		Verifier.Result result = verification;
		return !reopened && result != null && result.matches(origin, quarterTurns, mirrored) ? result.built : null;
	}

	/** True when the block at {@code local} (a position inside the box) is already built in the world, as of the last check. */
	public boolean isBuilt(BlockPos local) {
		Verifier.Result result = verification;
		return result != null && result.matches(origin, quarterTurns, mirrored) && result.built.get((local.getY() * sizeZ + local.getZ()) * sizeX + local.getX());
	}

	/** True when the last check found nothing left to build, nothing wrong and no part out of reach. */
	public boolean autoComplete() {
		Verifier.Result result = verification;
		return result != null && result.matches(origin, quarterTurns, mirrored) && result.isComplete();
	}

	/** Finished, as the user marked it or as the check of the world found it (unless the user opened it again). */
	public boolean complete() {
		return !reopened && (markedComplete || autoComplete());
	}

	/** The box of the placements window: a finished placement is opened again, any other is marked as finished. */
	public void toggleComplete() {
		if (complete()) {
			markedComplete = false;
			reopened(true);
		} else {
			markedComplete = true;
			reopened(false);
			// a finished placement is not the one the editor works on
			HologramRenderer.deselect(this);
		}
	}

	/** Restores an earlier position and orientation, for cancelling a move. */
	public void setTransform(BlockPos newOrigin, int turns, boolean mirror) {
		this.quarterTurns = turns & 3;
		this.mirrored = mirror;
		rebuild();
		this.origin = newOrigin;
	}

	public void moveTo(BlockPos newOrigin) {
		this.origin = newOrigin;
	}

	public void moveBy(int dx, int dy, int dz) {
		this.origin = origin.offset(dx, dy, dz);
	}

	/** Puts the middle of the hologram's bottom face on {@code target}. */
	public void placeCentered(BlockPos target) {
		this.origin = target.offset(-sizeX / 2, 0, -sizeZ / 2);
	}

	/** Turns the hologram 90 degrees clockwise about its own centre, so it stays where it is. */
	public void rotateClockwise() {
		BlockPos centreBefore = centre();
		quarterTurns = (quarterTurns + 1) & 3;
		rebuild();
		keepCentre(centreBefore);
	}

	/** Mirrors the hologram left to right about its own centre. */
	public void mirror() {
		BlockPos centreBefore = centre();
		mirrored = !mirrored;
		rebuild();
		keepCentre(centreBefore);
	}

	private BlockPos centre() {
		return origin.offset(sizeX / 2, 0, sizeZ / 2);
	}

	private void keepCentre(BlockPos centre) {
		this.origin = centre.offset(-sizeX / 2, 0, -sizeZ / 2);
	}

	/**
	 * The height of the fluid surface at the four top corners of the block at (x, y, z), worked out like the game does: the average of the
	 * blocks that meet at the corner (a source weighs more), full when fluid of the same kind lies above, and open space counting as empty.
	 */
	private float[] cornerHeights(int[] grid, int[] fluids, int[] levels, boolean[] occludes, int x, int y, int z, int number) {
		float[] out = new float[4];
		for (int cz = 0; cz < 2; cz++) {
			for (int cx = 0; cx < 2; cx++) {
				int stepX = cx == 1 ? 1 : -1, stepZ = cz == 1 ? 1 : -1;
				float sum = 0.0F, count = 0.0F;
				boolean full = false;
				for (int k = 0; k < 4 && !full; k++) {
					int nx = x + (k & 1) * stepX, nz = z + (k >> 1) * stepZ;
					int cell = nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ ? 0 : grid[(y * sizeZ + nz) * sizeX + nx];
					if (cell != 0 && fluids[cell] == fluids[number]) {
						int up = y + 1 >= sizeY ? 0 : grid[((y + 1) * sizeZ + nz) * sizeX + nx];
						if (up != 0 && fluids[up] == fluids[number]) {
							full = true;
						} else {
							float weight = levels[cell] == 0 ? 10.0F : 1.0F;
							sum += (float) fluidHeight(levels[cell]) * weight;
							count += weight;
						}
					} else if (cell == 0 || !occludes[cell]) {
						count += 1.0F;
					}
				}
				out[cz * 2 + cx] = full || count == 0.0F ? 1.0F : sum / count;
			}
		}
		return out;
	}

	/** Height of a fluid block of the given level, as a fraction of a block (a source is 8/9, a falling block is full). */
	private static double fluidHeight(int level) {
		return level >= 8 ? 1.0 : (8 - level) / 9.0;
	}

	/**
	 * Which way the fluid at (x, y, z) runs: down when it is falling, otherwise towards the lower
	 * neighbours (open space counts as empty), like the game's own flow direction.
	 */
	private int flowOf(int[] grid, int[] fluids, int[] levels, boolean[] occludes, int x, int y, int z, int number) {
		if (levels[number] >= 8) {
			return FALLING;
		}
		double mine = fluidHeight(levels[number]);
		double vx = 0.0, vz = 0.0;
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] step : steps) {
			int nx = x + step[0], nz = z + step[1];
			double other;
			int neighbour = nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ ? 0 : grid[(y * sizeZ + nz) * sizeX + nx];
			if (neighbour != 0 && fluids[neighbour] == fluids[number]) {
				other = fluidHeight(levels[neighbour]);
			} else if (neighbour != 0 && occludes[neighbour]) {
				continue;
			} else {
				// Open space: the fluid only runs towards it when there is more of the same fluid below it, to fall into.
				int below = nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ || y == 0 ? 0 : grid[((y - 1) * sizeZ + nz) * sizeX + nx];
				if (below == 0 || fluids[below] != fluids[number]) {
					continue;
				}
				other = fluidHeight(levels[below]) - 0.8888889;
			}
			vx += step[0] * (mine - other);
			vz += step[1] * (mine - other);
		}
		if (vx * vx + vz * vz < 0.01) {
			return NO_FLOW;
		}
		int direction = (int) Math.floorMod(Math.round(Math.atan2(vz, vx) / (Math.PI / 4.0)), 8L);
		return FLOW_FIRST + direction;
	}

	/**
	 * Water and lava blocks, bubble columns (water that has no model of its own, over soul sand or magma), and the blocks that hold water
	 * (a waterlogged slab, kelp, seagrass), whose water is drawn together with their own model.
	 */
	private static int fluidOf(BlockState state) {
		if (state.getBlock() instanceof BubbleColumnBlock) {
			return WATER;
		}
		if (!(state.getBlock() instanceof LiquidBlock)) {
			return state.getFluidState().is(FluidTags.WATER) ? WATER : NO_FLUID;
		}
		return state.getFluidState().is(FluidTags.WATER) ? WATER : LAVA;
	}

	/** True for a fluid that is only a fluid; a block that holds water has a model of its own as well. */
	private static boolean onlyFluid(BlockState state) {
		return state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof BubbleColumnBlock;
	}

	/** The x a block of the schematic ends up at after the mirror and the turn (before moving the box to the origin). */
	private static int turnedX(int px, int pz, boolean mirror, int turns) {
		int x = mirror ? -px : px;
		return switch (turns) {
			case 1 -> -pz;
			case 2 -> -x;
			case 3 -> pz;
			default -> x;
		};
	}

	/** The z a block of the schematic ends up at after the mirror and the turn. A clockwise quarter turn maps (x, z) to (-z, x), as BlockPos.rotate does. */
	private static int turnedZ(int px, int pz, boolean mirror, int turns) {
		int x = mirror ? -px : px;
		return switch (turns) {
			case 1 -> x;
			case 2 -> -pz;
			case 3 -> -x;
			default -> pz;
		};
	}

	private void rebuild() {
		solid = null;
		ghosts = null;
		blockGhosts = null;
		blockEntityDefinitions = List.of();
		entityDefinitions = List.of();
		Rotation rotation = switch (quarterTurns) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};

		// 1. The box that holds every turned block.
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Region region : schematic.regions()) {
			int x0 = region.origin().getX(), y0 = region.origin().getY(), z0 = region.origin().getZ();
			int x1 = x0 + region.sizeX() - 1, y1 = y0 + region.sizeY() - 1, z1 = z0 + region.sizeZ() - 1;
			for (int cx : new int[] {x0, x1}) {
				for (int cy : new int[] {y0, y1}) {
					for (int cz : new int[] {z0, z1}) {
						int tx = turnedX(cx, cz, mirrored, quarterTurns);
						int tz = turnedZ(cx, cz, mirrored, quarterTurns);
						minX = Math.min(minX, tx); maxX = Math.max(maxX, tx);
						minY = Math.min(minY, cy); maxY = Math.max(maxY, cy);
						minZ = Math.min(minZ, tz); maxZ = Math.max(maxZ, tz);
					}
				}
			}
		}
		if (minX > maxX) {
			entries = List.of();
			sections = List.of();
			grid = new int[0];
			solidCells = new int[0];
			gridStates = List.of();
			sizeX = sizeY = sizeZ = 0;
			return;
		}
		sizeX = maxX - minX + 1;
		sizeY = maxY - minY + 1;
		sizeZ = maxZ - minZ + 1;
		long volume = (long) sizeX * sizeY * sizeZ;
		if (volume > MAX_VOLUME) {
			throw new IllegalArgumentException("The schematic is too big to show (" + sizeX + " x " + sizeY + " x " + sizeZ + ")");
		}

		// The schematic's entities, moved into the same box. A point is turned like a block, except that a
		// block index n becomes -n while a position inside it becomes 1 - n (the far side of the cell).
		List<GhostEntities.Definition> definitions = new ArrayList<>();
		for (Region region : schematic.regions()) {
			for (CompoundTag data : region.entities()) {
				ListTag pos = data.getListOrEmpty("Pos");
				if (pos.size() < 3) {
					continue;
				}
				double px = region.entityBase().getX() + pos.getDoubleOr(0, 0.0);
				double py = region.entityBase().getY() + pos.getDoubleOr(1, 0.0);
				double pz = region.entityBase().getZ() + pos.getDoubleOr(2, 0.0);
				double x = mirrored ? 1.0 - px : px;
				double tx, tz;
				switch (quarterTurns) {
					case 1 -> { tx = 1.0 - pz; tz = x; }
					case 2 -> { tx = 1.0 - x; tz = 1.0 - pz; }
					case 3 -> { tx = pz; tz = 1.0 - x; }
					default -> { tx = x; tz = pz; }
				}
				definitions.add(new GhostEntities.Definition(data, tx - minX, py - minY, tz - minZ));
			}
		}
		entityDefinitions = definitions;

		// 2. One number per cell: 0 is empty, anything else is a block state of this hologram.
		int[] grid = new int[(int) volume];
		it.unimi.dsi.fastutil.ints.IntArrayList solid = new it.unimi.dsi.fastutil.ints.IntArrayList();
		List<BlockState> states = new ArrayList<>();
		states.add(null);
		Map<BlockState, Integer> stateNumber = new HashMap<>();
		boolean[] occludes = new boolean[16];
		boolean[] solids = new boolean[16];
		int[] fluids = new int[16];
		int[] levels = new int[16];
		for (Region region : schematic.regions()) {
			List<BlockState> palette = region.palette();
			int[] mapped = new int[palette.size()];
			for (int i = 0; i < mapped.length; i++) {
				BlockState state = palette.get(i);
				if (state.isAir()) {
					mapped[i] = 0;
					continue;
				}
				if (mirrored) {
					state = state.mirror(Mirror.FRONT_BACK);
				}
				state = state.rotate(rotation);
				Integer number = stateNumber.get(state);
				if (number == null) {
					number = states.size();
					states.add(state);
					stateNumber.put(state, number);
					if (number >= occludes.length) {
						occludes = java.util.Arrays.copyOf(occludes, occludes.length * 2);
						solids = java.util.Arrays.copyOf(solids, solids.length * 2);
						fluids = java.util.Arrays.copyOf(fluids, fluids.length * 2);
						levels = java.util.Arrays.copyOf(levels, levels.length * 2);
					}
					occludes[number] = state.canOcclude();
					// Only a whole cube hides the face of its neighbour: a repeater, a chest or a slab leaves part of it showing.
					solids[number] = state.canOcclude() && net.minecraft.world.level.block.Block.isShapeFullBlock(state.getOcclusionShape());
					fluids[number] = fluidOf(state);
					if (fluids[number] != NO_FLUID) {
						// A bubble column and a block that holds water are water sources.
						levels[number] = state.getBlock() instanceof LiquidBlock ? state.getValue(LiquidBlock.LEVEL) : 0;
					}
				}
				mapped[i] = number;
			}
			for (int y = 0; y < region.sizeY(); y++) {
				for (int z = 0; z < region.sizeZ(); z++) {
					for (int x = 0; x < region.sizeX(); x++) {
						int number = mapped[region.paletteIndex(x, y, z)];
						if (number == 0) {
							continue;
						}
						int px = region.origin().getX() + x;
						int pz = region.origin().getZ() + z;
						int lx = turnedX(px, pz, mirrored, quarterTurns) - minX;
						int ly = region.origin().getY() + y - minY;
						int lz = turnedZ(px, pz, mirrored, quarterTurns) - minZ;
						int cell = (ly * sizeZ + lz) * sizeX + lx;
						if (grid[cell] == 0) {
							solid.add(cell);
						}
						grid[cell] = number;
					}
				}
			}
		}

		this.grid = grid;
		int[] solidArray = solid.toIntArray();
		java.util.Arrays.sort(solidArray);
		this.solidCells = solidArray;
		stampsX = (sizeX + 15) >> 4;
		stampsY = (sizeY + 15) >> 4;
		stampsZ = (sizeZ + 15) >> 4;
		builtStamps = new int[stampsX * stampsY * stampsZ];
		builtEpoch++;
		this.gridStates = states;

		// The blocks drawn by block entities (chests, signs...), with their saved data, moved into the same box.
		List<GhostBlockEntities.Definition> blockDefinitions = new ArrayList<>();
		for (Region region : schematic.regions()) {
			for (Map.Entry<BlockPos, CompoundTag> saved : region.blockEntities().entrySet()) {
				int px = region.origin().getX() + saved.getKey().getX();
				int pz = region.origin().getZ() + saved.getKey().getZ();
				int lx = turnedX(px, pz, mirrored, quarterTurns) - minX;
				int ly = region.origin().getY() + saved.getKey().getY() - minY;
				int lz = turnedZ(px, pz, mirrored, quarterTurns) - minZ;
				if (lx < 0 || ly < 0 || lz < 0 || lx >= sizeX || ly >= sizeY || lz >= sizeZ) {
					continue;
				}
				int number = grid[(ly * sizeZ + lz) * sizeX + lx];
				if (number != 0 && GhostBlockEntities.isBlockEntity(states.get(number))) {
					blockDefinitions.add(new GhostBlockEntities.Definition(new BlockPos(lx, ly, lz), states.get(number), saved.getValue()));
				}
			}
		}
		blockEntityDefinitions = blockDefinitions;

		// 3. Keep the blocks that have at least one face not hidden by an opaque neighbour.
		this.occludesByNumber = occludes;
		this.solidByNumber = solids;
		this.fluidByNumber = fluids;
		this.levelByNumber = levels;
		dropLayerCache();
		List<Entry> out = new ArrayList<>();
		Map<Long, Section> cells = new HashMap<>();
		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					Entry entry = entryAt(x, y, z, false);
					if (entry != null) {
						out.add(entry);
						long key = ((long) (x >> 4) << 40) ^ ((long) (y >> 4) << 20) ^ (z >> 4);
						cells.computeIfAbsent(key, k -> new Section()).add(entry);
					}
				}
			}
		}
		entries = out;
		sections = new ArrayList<>(cells.values());
	}

	/**
	 * The entry of the block at (x, y, z), or null when it is air or every face of it is hidden. With {@code layerOnly} the blocks above and
	 * below do not hide anything (the layer is shown on its own, so its top and bottom faces are there to see).
	 */
	private Entry entryAt(int x, int y, int z, boolean layerOnly) {
		int number = grid[(y * sizeZ + z) * sizeX + x];
		if (number == 0) {
			return null;
		}
		int faces = 0, modelFaces = 0;
		boolean waterlogged = fluidByNumber[number] != NO_FLUID && !onlyFluid(gridStates.get(number));
		for (Direction direction : Direction.values()) {
			int nx = x + direction.getStepX(), ny = y + direction.getStepY(), nz = z + direction.getStepZ();
			boolean inside = nx >= 0 && ny >= 0 && nz >= 0 && nx < sizeX && ny < sizeY && nz < sizeZ
				&& !(layerOnly && direction.getAxis() == Direction.Axis.Y);
			int neighbour = inside ? grid[(ny * sizeZ + nz) * sizeX + nx] : 0;
			boolean opaque = inside && solidByNumber[neighbour];
			boolean hidden = opaque || (inside && fluidByNumber[number] != NO_FLUID && fluidByNumber[neighbour] == fluidByNumber[number]);
			if (!hidden) {
				faces |= 1 << direction.get3DDataValue();
			}
			if (!opaque) {
				modelFaces |= 1 << direction.get3DDataValue();
			}
		}
		// A block that holds water is still there to see when its water is hidden by more water around it (kelp in the sea).
		if (faces == 0 && !(waterlogged && modelFaces != 0)) {
			return null;
		}
		if (!waterlogged) {
			modelFaces = faces;
		}
		int fluid = fluidByNumber[number];
		int flow = fluid == NO_FLUID ? NO_FLOW : flowOf(grid, fluidByNumber, levelByNumber, occludesByNumber, x, y, z, number);
		float[] heights = fluid == NO_FLUID ? null : cornerHeights(grid, fluidByNumber, levelByNumber, occludesByNumber, x, y, z, number);
		return new Entry(new BlockPos(x, y, z), gridStates.get(number), faces, fluid, flow, heights, waterlogged, modelFaces);
	}

	/** The block-by-block data kept from the last rebuild, to work out the faces of a single layer later. */
	private boolean[] occludesByNumber = new boolean[0];
	private boolean[] solidByNumber = new boolean[0];
	private int[] fluidByNumber = new int[0];
	private int[] levelByNumber = new int[0];
	private int layerCacheNumber = -1;
	private List<Section> layerCache = List.of();

	/**
	 * The cells of one horizontal layer shown on its own: unlike {@link #sections()} they keep the faces that look up and down, which the
	 * blocks above and below hide when the whole hologram is shown. Worked out when first asked for and kept for the layer last used.
	 */
	public synchronized List<Section> layerSections(int layer) {
		if (layer < 0 || layer >= sizeY) {
			return List.of();
		}
		if (layerCacheNumber != layer) {
			dropLayerCache();
			Map<Long, Section> cells = new HashMap<>();
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					Entry entry = entryAt(x, layer, z, true);
					if (entry != null) {
						long key = ((long) (x >> 4) << 40) ^ (z >> 4);
						cells.computeIfAbsent(key, k -> new Section()).add(entry);
					}
				}
			}
			layerCache = new ArrayList<>(cells.values());
			layerCacheNumber = layer;
		}
		return layerCache;
	}

	private synchronized void dropLayerCache() {
		HologramRenderer.forgetSections(layerCache);
		layerCache = List.of();
		layerCacheNumber = -1;
	}
}
