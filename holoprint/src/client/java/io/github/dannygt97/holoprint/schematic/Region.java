package io.github.dannygt97.holoprint.schematic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One box of blocks inside a schematic. {@code origin} is the box's minimum corner relative to the
 * schematic's own origin; positions inside the box are local (0 .. size - 1 on every axis).
 */
public final class Region {
	private final String name;
	private final BlockPos origin;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final List<BlockState> palette = new ArrayList<>();
	private final Map<BlockState, Integer> paletteIndex = new HashMap<>();
	private final int[] indices;
	private final Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
	private final List<CompoundTag> entities = new ArrayList<>();
	/** The corner that the entities' positions are relative to: the stored position of the region, which is not the minimum corner when its size is negative. */
	private BlockPos entityBase;

	public Region(String name, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
		if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			throw new IllegalArgumentException("Region " + name + " has an empty size");
		}
		long volume = (long) sizeX * sizeY * sizeZ;
		if (volume > Integer.MAX_VALUE - 8) {
			throw new IllegalArgumentException("Region " + name + " is too big (" + volume + " blocks)");
		}
		this.name = name;
		this.origin = origin;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.indices = new int[(int) volume];
		// Air is always palette entry 0, so a new region is empty and the file format stays familiar.
		indexOf(Blocks.AIR.defaultBlockState());
	}

	public String name() { return name; }
	public BlockPos origin() { return origin; }
	public int sizeX() { return sizeX; }
	public int sizeY() { return sizeY; }
	public int sizeZ() { return sizeZ; }
	public long volume() { return indices.length; }
	public List<BlockState> palette() { return palette; }
	public Map<BlockPos, CompoundTag> blockEntities() { return blockEntities; }
	public List<CompoundTag> entities() { return entities; }
	public BlockPos entityBase() { return entityBase != null ? entityBase : origin; }
	public void entityBase(BlockPos base) { this.entityBase = base; }

	public boolean contains(int x, int y, int z) {
		return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
	}

	/** Position of a block in the packed index order used by .litematic: layers of z rows of x. */
	public int indexAt(int x, int y, int z) {
		return (y * sizeZ + z) * sizeX + x;
	}

	/** The palette number of the block at a local position, without looking the block up. */
	public int paletteIndex(int x, int y, int z) {
		return indices[indexAt(x, y, z)];
	}

	public BlockState get(int x, int y, int z) {
		return palette.get(indices[indexAt(x, y, z)]);
	}

	public void set(int x, int y, int z, BlockState state) {
		indices[indexAt(x, y, z)] = indexOf(state);
	}

	/** Palette index of {@code state}, adding it when this is the first time it is used. */
	public int indexOf(BlockState state) {
		Integer found = paletteIndex.get(state);
		if (found != null) {
			return found;
		}
		int created = palette.size();
		palette.add(state);
		paletteIndex.put(state, created);
		return created;
	}

	int[] rawIndices() {
		return indices;
	}

	/** Number of blocks that are not air. */
	public long countBlocks() {
		long count = 0;
		for (int index : indices) {
			if (!palette.get(index).isAir()) {
				count++;
			}
		}
		return count;
	}
}
