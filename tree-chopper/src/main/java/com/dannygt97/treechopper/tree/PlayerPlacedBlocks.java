package com.dannygt97.treechopper.tree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;

// Remembers which log-like blocks were put down by a player (or a dispenser), so felling never
// treats a house, fence or tree house as part of a natural tree. Vanilla keeps no such marker, so
// positions are stored per chunk in a persistent data attachment: saved with the chunk, gone with
// the chunk, nothing to clean up. Natural blocks (worldgen, grown trees) are never recorded, and
// stripping a natural log with an axe leaves it unrecorded - it is still part of its tree.
public final class PlayerPlacedBlocks {
	private static final AttachmentType<Set<Long>> PLACED = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath("treechopper", "placed_blocks"),
		Codec.LONG.listOf().<Set<Long>>xmap(HashSet::new, ArrayList::new)
	);

	private PlayerPlacedBlocks() {}

	// Every block the felling logic would otherwise consume: all wood types (logs, wood, stripped
	// variants, stems, hyphae) and the nether tree canopy blocks.
	public static boolean shouldTrack(final BlockState state) {
		return state.is(BlockTags.LOGS) || isNetherCanopy(state);
	}

	public static boolean isNetherCanopy(final BlockState state) {
		return state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.WARPED_WART_BLOCK) || state.is(Blocks.SHROOMLIGHT);
	}

	public static void mark(final Level level, final BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		chunk.getAttachedOrCreate(PLACED, HashSet::new).add(pos.asLong());
		chunk.markUnsaved();
	}

	public static void unmark(final Level level, final BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		Set<Long> placed = chunk.getAttached(PLACED);
		if (placed != null && placed.remove(pos.asLong())) {
			if (placed.isEmpty()) {
				chunk.removeAttached(PLACED);
			}
			chunk.markUnsaved();
		}
	}

	public static boolean isPlaced(final Level level, final BlockPos pos) {
		Set<Long> placed = level.getChunkAt(pos).getAttached(PLACED);
		return placed != null && placed.contains(pos.asLong());
	}
}
