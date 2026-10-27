package io.github.dannygt97.holoprint.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The size of the text Holoprint puts on the screen on its own (the editor's legend, the explanation of the highlighted tool, the pinned material
 * list). They all share it, so they read alike, and it comes on top of the game's GUI scale (Video Settings), which is what sizes everything else.
 * Drawing goes between {@link #begin} and {@link #end} in the coordinates of {@link #width} and {@link #height}.
 */
public final class HudText {
	public static final float SCALE = 0.8F;

	private HudText() {}

	public static void begin(GuiGraphicsExtractor graphics) {
		graphics.pose().pushMatrix();
		graphics.pose().scale(SCALE, SCALE);
	}

	public static void end(GuiGraphicsExtractor graphics) {
		graphics.pose().popMatrix();
	}

	/** The width of the screen in the units drawing uses between begin and end. */
	public static int width(GuiGraphicsExtractor graphics) {
		return Math.round(graphics.guiWidth() / SCALE);
	}

	/** The height of the screen in the units drawing uses between begin and end. */
	public static int height(GuiGraphicsExtractor graphics) {
		return Math.round(graphics.guiHeight() / SCALE);
	}
}
