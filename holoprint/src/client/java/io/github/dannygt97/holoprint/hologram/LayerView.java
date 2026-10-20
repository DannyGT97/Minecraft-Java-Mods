package io.github.dannygt97.holoprint.hologram;

import net.minecraft.client.Minecraft;

/**
 * Which layers of the holograms are shown: all of them, or a single horizontal layer (counted from the bottom of each hologram,
 * so the same layer of every pasted copy shows together). What is hidden is also not drawn, outlined or placed by easy placement.
 */
public final class LayerView {
	public enum Mode {
		ALL, SINGLE
	}

	private static volatile Mode mode = Mode.ALL;
	/** The layer shown in {@link Mode#SINGLE}, 0 for the bottom one. */
	private static volatile int layer;

	private LayerView() {}

	public static Mode mode() {
		return mode;
	}

	/** The layer shown in single-layer mode, 0 for the bottom one. */
	public static int layer() {
		return layer;
	}

	/** True when the blocks at this height of a hologram (0 at its bottom) are shown. */
	public static boolean shows(int localY) {
		return mode == Mode.ALL || localY == layer;
	}

	/** The number of layers of the tallest visible hologram. */
	public static int height() {
		int height = 0;
		for (Hologram hologram : HologramRenderer.all()) {
			if (hologram.visible()) {
				height = Math.max(height, hologram.sizeY());
			}
		}
		return height;
	}

	/** Switches between all layers and a single one. A single layer starts at the height the player stands at. */
	public static void toggleMode(Minecraft mc) {
		if (mode == Mode.ALL) {
			mode = Mode.SINGLE;
			Hologram reference = HologramRenderer.active();
			if (reference != null && mc.player != null) {
				layer = mc.player.blockPosition().getY() - reference.origin().getY();
			}
			clamp();
		} else {
			mode = Mode.ALL;
		}
	}

	/** Back to showing every layer (on entering a world, before its own saved view is put back). */
	public static void reset() {
		mode = Mode.ALL;
		layer = 0;
	}

	/** Puts back a view that was saved with a world's placements. The layer is not limited to the height of the holograms, which may not be back yet. */
	public static void restore(boolean single, int savedLayer) {
		mode = single ? Mode.SINGLE : Mode.ALL;
		layer = Math.max(0, savedLayer);
	}

	/** Moves the shown layer up or down; returns false when it is not in single-layer mode. */
	public static boolean move(int delta) {
		if (mode != Mode.SINGLE) {
			return false;
		}
		layer += delta;
		clamp();
		return true;
	}

	private static void clamp() {
		layer = Math.max(0, Math.min(Math.max(0, height() - 1), layer));
	}
}
