package io.github.dannygt97.holoprint.selection;

import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.schematic.Schematic;
import net.minecraft.core.BlockPos;

/** What Ctrl+C last copied: a schematic with the orientation it was copied in. */
public final class Clipboard {
	private static Schematic schematic;
	private static int quarterTurns;
	private static boolean mirrored;

	private Clipboard() {}

	public static boolean isEmpty() {
		return schematic == null;
	}

	/** What is on the clipboard, or null when it is empty. */
	public static Schematic schematic() {
		return schematic;
	}

	public static void set(Schematic copied, int turns, boolean mirror) {
		schematic = copied;
		quarterTurns = turns;
		mirrored = mirror;
	}

	/** A new hologram of the clipboard contents at {@code origin}, or null when it is empty. */
	public static Hologram newHologram(BlockPos origin) {
		if (schematic == null) {
			return null;
		}
		Hologram hologram = new Hologram(schematic, origin);
		hologram.setTransform(origin, quarterTurns, mirrored);
		return hologram;
	}
}
