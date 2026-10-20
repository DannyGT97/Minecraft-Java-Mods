package io.github.dannygt97.holoprint.schematic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/** A named set of regions plus the descriptive data stored in the file. */
public final class Schematic {
	private String name = "";
	private String author = "";
	private String description = "";
	private long timeCreated;
	private long timeModified;
	private int dataVersion;
	/** Blocks the file named that this game does not know; they load as air. */
	private final List<String> unknownBlocks = new ArrayList<>();
	/** How many positions of the file held a block this game does not know. */
	private long unknownCount;
	private final List<Region> regions = new ArrayList<>();
	/** The file this schematic was read from or saved to, or null when it only exists in memory. */
	private java.nio.file.Path source;

	public java.nio.file.Path source() { return source; }
	public void source(java.nio.file.Path value) { this.source = value; }
	public String name() { return name; }
	public void name(String value) { this.name = value; }
	public String author() { return author; }
	public void author(String value) { this.author = value; }
	public String description() { return description; }
	public void description(String value) { this.description = value; }
	public long timeCreated() { return timeCreated; }
	public void timeCreated(long value) { this.timeCreated = value; }
	public long timeModified() { return timeModified; }
	public void timeModified(long value) { this.timeModified = value; }
	/** Minecraft data version the file was saved with (0 when unknown). */
	public int dataVersion() { return dataVersion; }
	public void dataVersion(int value) { this.dataVersion = value; }
	public List<String> unknownBlocks() { return unknownBlocks; }
	public long unknownCount() { return unknownCount; }

	/** Remembers {@code count} positions that held the block {@code name}, which this game does not have. */
	void addUnknown(String name, long count) {
		if (!unknownBlocks.contains(name)) {
			unknownBlocks.add(name);
		}
		unknownCount += count;
	}
	public List<Region> regions() { return regions; }

	public long totalBlocks() {
		long total = 0;
		for (Region region : regions) {
			total += region.countBlocks();
		}
		return total;
	}

	public long totalVolume() {
		long total = 0;
		for (Region region : regions) {
			total += region.volume();
		}
		return total;
	}

	/** Size of the smallest box that holds every region. */
	public BlockPos enclosingSize() {
		if (regions.isEmpty()) {
			return BlockPos.ZERO;
		}
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Region r : regions) {
			minX = Math.min(minX, r.origin().getX());
			minY = Math.min(minY, r.origin().getY());
			minZ = Math.min(minZ, r.origin().getZ());
			maxX = Math.max(maxX, r.origin().getX() + r.sizeX());
			maxY = Math.max(maxY, r.origin().getY() + r.sizeY());
			maxZ = Math.max(maxZ, r.origin().getZ() + r.sizeZ());
		}
		return new BlockPos(maxX - minX, maxY - minY, maxZ - minZ);
	}
}
