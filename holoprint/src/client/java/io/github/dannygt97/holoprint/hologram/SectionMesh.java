package io.github.dannygt97.holoprint.hologram;

import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * The quads of one 16-block cell of a hologram, worked out once and replayed every frame: it holds each
 * visible face with its vertices relative to the hologram's origin, so drawing a frame is only copying
 * numbers, with no model lookups. Which faces are drawn is still decided each frame (faces turned away
 * from the camera and blocks that are already built are skipped).
 */
final class SectionMesh {
	/** Per quad four vertices of x, y, z, u, v. */
	final float[] vertices;
	final int quads;
	/** Per quad the block it belongs to, relative to the hologram's origin (three numbers). */
	final int[] block;
	/** Per quad the index of its block in the hologram's grid, for finding out whether it is built. */
	final int[] cell;
	/** Per quad the face of the block it is culled with (0 to 5 in {@code Direction.get3DDataValue} order), or -1 when it is always drawn. */
	final byte[] cull;
	/** Per quad the direction it faces, for its normal. */
	final byte[] normal;
	/** Per quad a tint (or a fluid colour) as RGB, and whether it replaces the hologram colour or multiplies it. */
	final int[] rgb;
	final boolean[] absolute;
	final int[] light;
	/** The fluid blocks that flow, which get arrows drawn on them. */
	final List<Hologram.Entry> arrows;
	/** True when some quad took a biome colour from the world, so the mesh goes stale when the hologram moves or the world loads. */
	final boolean tinted;
	final boolean builtWithBiomeColors;
	/** Whether the fluids were built with the game's textures (moving) or as flat colours. */
	final boolean fluidTextured;
	final BlockPos builtOrigin;
	final long builtAt;
	long lastUsed;
	/** The number of the 16-block cell of the box this mesh belongs to, for knowing when its built blocks changed (see Hologram.builtStamp). */
	final int stampKey;
	/** What the mesh keeps in the graphics card, once it has been drawn (see RetainedMeshes). */
	volatile RetainedMeshes.Gpu gpu;

	SectionMesh(float[] vertices, int quads, int[] block, int[] cell, byte[] cull, byte[] normal, int[] rgb, boolean[] absolute, int[] light,
			List<Hologram.Entry> arrows, boolean tinted, boolean builtWithBiomeColors, boolean fluidTextured, BlockPos builtOrigin, long builtAt, int stampKey) {
			this.stampKey = stampKey;
		this.vertices = vertices;
		this.quads = quads;
		this.block = block;
		this.cell = cell;
		this.cull = cull;
		this.normal = normal;
		this.rgb = rgb;
		this.absolute = absolute;
		this.light = light;
		this.arrows = arrows;
		this.tinted = tinted;
		this.builtWithBiomeColors = builtWithBiomeColors;
		this.fluidTextured = fluidTextured;
		this.builtOrigin = builtOrigin;
		this.builtAt = builtAt;
	}

	/** Rough memory held, in bytes, for the cache's ceiling. */
	long weight() {
		return (long) quads * (20 * 4 + 3 * 4 + 4 + 1 + 1 + 4 + 1 + 4);
	}
}
