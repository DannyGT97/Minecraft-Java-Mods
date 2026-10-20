package io.github.dannygt97.holoprint.hologram;

/** Public access to what the retained renderer drew last frame, for the tests that measure it. */
public final class RetainedMeshesProbe {
	private RetainedMeshesProbe() {}

	public static int queued() {
		return RetainedMeshes.lastQueued;
	}

	public static int cells() {
		return RetainedMeshes.lastCells;
	}

	public static int quads() {
		return RetainedMeshes.lastQuadsDrawn;
	}

	public static int made() {
		return RetainedMeshes.lastMade;
	}
}
