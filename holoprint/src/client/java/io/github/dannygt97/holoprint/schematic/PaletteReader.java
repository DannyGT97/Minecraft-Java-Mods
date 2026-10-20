package io.github.dannygt97.holoprint.schematic;

import java.io.IOException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns the block palette of a file into the palette of a {@link Region}. A block this game does not
 * have becomes air, and how many positions held one is remembered by name so the player is told.
 */
final class PaletteReader {
	private final Region region;
	private final Schematic schematic;
	private final int[] mapping;
	private final String[] unknownName;
	private final long[] uses;

	PaletteReader(Region region, Schematic schematic, int size) {
		this.region = region;
		this.schematic = schematic;
		this.mapping = new int[size];
		this.unknownName = new String[size];
		this.uses = new long[size];
	}

	/** Defines palette entry {@code fileIndex}; {@code tag} is a block state already brought up to date. */
	void define(int fileIndex, CompoundTag tag) {
		if (BlockStateTags.isKnown(tag)) {
			mapping[fileIndex] = region.indexOf(BlockStateTags.read(tag));
		} else {
			unknownName[fileIndex] = BlockStateTags.nameOf(tag);
			mapping[fileIndex] = region.indexOf(Blocks.AIR.defaultBlockState());
		}
	}

	int size() {
		return mapping.length;
	}

	/** The region palette number for a palette number of the file. */
	int map(int fileIndex) throws IOException {
		if (fileIndex < 0 || fileIndex >= mapping.length) {
			throw new IOException("Region " + region.name() + " uses palette entry " + fileIndex + " but the palette has " + mapping.length);
		}
		uses[fileIndex]++;
		return mapping[fileIndex];
	}

	/** Tells the schematic which blocks were left out and how many positions they covered. */
	void finish() {
		for (int i = 0; i < mapping.length; i++) {
			if (unknownName[i] != null && uses[i] > 0) {
				schematic.addUnknown(unknownName[i], uses[i]);
			}
		}
	}

	/** The state of a position, for tests. */
	BlockState stateOf(int fileIndex) {
		return region.palette().get(mapping[fileIndex]);
	}
}
