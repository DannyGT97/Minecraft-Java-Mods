package io.github.dannygt97.holoprint.selection;

import java.util.List;

import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.schematic.Schematic;
import net.minecraft.core.BlockPos;

/** What Ctrl+C last copied: a schematic with the orientation it was copied in. */
public final class Clipboard {
	private static Schematic schematic;
	private static int quarterTurns;
	private static boolean mirrored;
	private static List<Hologram.Replacement> replacements = List.of();

	private Clipboard() {}

	public static boolean isEmpty() {
		return schematic == null;
	}

	/** What is on the clipboard, or null when it is empty. */
	public static Schematic schematic() {
		return schematic;
	}

	public static void set(Schematic copied, int turns, boolean mirror) {
		set(copied, turns, mirror, List.of());
	}

	/** What a hologram shows, with the replacements made to it. */
	public static void set(Hologram copied) {
		set(copied.schematic(), copied.quarterTurns(), copied.mirrored(), copied.replacements());
	}

	private static void set(Schematic copied, int turns, boolean mirror, List<Hologram.Replacement> replaced) {
		schematic = copied;
		quarterTurns = turns;
		mirrored = mirror;
		replacements = replaced;
	}

	/** A new hologram of the clipboard contents at {@code origin}, or null when it is empty. */
	public static Hologram newHologram(BlockPos origin) {
		if (schematic == null) {
			return null;
		}
		return Hologram.placed(schematic, origin, quarterTurns, mirrored, replacements);
	}
}
