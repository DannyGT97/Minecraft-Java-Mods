package com.dannygt97.inventoryfilters.client.gui;

/** Suppresses the vanilla hotbar pop animation caused by server-applied preset changes. */
public final class PresetLoadAnimationSuppressor {
	private static final long WINDOW_NANOS = 3_000_000_000L;
	private static long suppressUntilNanos;
	private static int hotbarSlotMask;

	private PresetLoadAnimationSuppressor() {}

	public static void suppressNextHotbarPop(java.util.List<String> presetSlots) {
		hotbarSlotMask = 0;
		for (int slot = 0; slot < Math.min(9, presetSlots.size()); slot++) {
			if (!presetSlots.get(slot).isEmpty()) hotbarSlotMask |= 1 << slot;
		}
		suppressUntilNanos = System.nanoTime() + WINDOW_NANOS;
	}

	public static boolean shouldSuppressHotbarSlot(int slot) {
		return System.nanoTime() < suppressUntilNanos
			&& slot >= 0 && slot < 9 && (hotbarSlotMask & (1 << slot)) != 0;
	}
}
