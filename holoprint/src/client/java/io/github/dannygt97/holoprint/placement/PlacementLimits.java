package io.github.dannygt97.holoprint.placement;

import io.github.dannygt97.holoprint.config.HoloprintConfig;

/** Limits that keep the world light; they are options of the configuration window. */
public final class PlacementLimits {
	private PlacementLimits() {}

	/** How many schematic placements can exist at once. */
	public static int max() {
		return HoloprintConfig.get().maxPlacements;
	}
}
