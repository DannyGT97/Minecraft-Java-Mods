package io.github.dannygt97.holoprint.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A small square button that shows a picture instead of a letter (an eye, a padlock, a bin). When
 * {@code crossedOut} is true the picture is drawn dimmed with a red slash through it, like a hidden eye.
 */
public final class IconButton extends Button {
	private final String icon;
	private final boolean lit;
	private final boolean crossedOut;

	public IconButton(int x, int y, int size, String icon, Component tooltip, boolean lit, boolean crossedOut, Runnable action) {
		super(x, y, size, size, tooltip, b -> action.run(), DEFAULT_NARRATION);
		this.icon = icon;
		this.lit = lit;
		this.crossedOut = crossedOut;
		setTooltip(Tooltip.create(tooltip));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();
		int size = getWidth();
		// The game's own button background, like every other button of the settings screens.
		extractDefaultSprite(graphics);
		graphics.fill(x + 2, y + 2, x + size - 2, y + size - 2, active ? 0xAA000000 : 0x66000000);
		int color = !active ? 0xFF707070 : crossedOut ? 0xFF909090 : lit ? 0xFFFFD166 : 0xFFFFFFFF;
		Icons.draw(graphics, icon, x, y, size, color);
		if (crossedOut) {
			for (int i = 2; i < size - 2; i++) {
				graphics.fill(x + i, y + size - 1 - i, x + i + 1, y + size - i, 0xFFFF5555);
			}
		}
	}
}
