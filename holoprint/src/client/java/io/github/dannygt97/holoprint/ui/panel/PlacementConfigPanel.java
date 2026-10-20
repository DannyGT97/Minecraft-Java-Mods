package io.github.dannygt97.holoprint.ui.panel;

import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.placement.Editor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The settings of one schematic placement: name, position with plus and minus buttons, rotation,
 * mirror and the enabled, visible and locked switches.
 */
public final class PlacementConfigPanel extends EditorPanel {
	private static final int WIDTH = 364;
	private static final int[] COLUMNS = {PAD, PAD + 106, PAD + 212};
	private static final String[] AXES = {"X", "Y", "Z"};

	private final float scale = fit(214);
	private final Hologram hologram;
	private final String name;

	public PlacementConfigPanel(Hologram hologram, String name) {
		this.hologram = hologram;
		this.name = name;
	}

	private int v(int value) {
		return Math.round(value * scale);
	}

	@Override
	public Component title() {
		return Component.translatable("holoprint.panel.placement_config");
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public int height() {
		return 26 + v(214);
	}

	private static int amount() {
		return Minecraft.getInstance().hasShiftDown() ? 8 : 1;
	}

	/** Moves the placement one block, unless it is locked. */
	private void step(Host host, int dx, int dy, int dz) {
		if (!hologram.locked()) {
			hologram.moveBy(dx, dy, dz);
			host.rebuild();
		}
	}

	private void orient(Host host, int turns, boolean mirrored) {
		if (!hologram.locked()) {
			hologram.setTransform(hologram.origin(), turns, mirrored);
			host.rebuild();
		}
	}

	@Override
	public void init(Host host, int x, int y) {
		box(host, x + PAD + 48, y, 220, hologram.displayName().isBlank() ? name : hologram.displayName()).setResponder(hologram::label);
		// Position: a box per axis where the number can be typed, with - and + beside it (Litematica style).
		BlockPos o = hologram.origin();
		int[] values = {o.getX(), o.getY(), o.getZ()};
		for (int i = 0; i < 3; i++) {
			int axis = i;
			int bx = x + COLUMNS[i] + 12;
			EditBox coordinate = box(host, bx, y + v(46), 50, String.valueOf(values[i]));
			coordinate.setMaxLength(9);
			coordinate.setResponder(text -> {
				try {
					int value = Integer.parseInt(text.trim());
					if (!hologram.locked()) {
						BlockPos p = hologram.origin();
						hologram.moveTo(new BlockPos(axis == 0 ? value : p.getX(), axis == 1 ? value : p.getY(), axis == 2 ? value : p.getZ()));
					}
				} catch (NumberFormatException ignored) {
					// not a number yet
				}
			});
			small(host, bx + 52, y + v(46) + 1, 16, "-", () -> step(host, axis == 0 ? -amount() : 0, axis == 1 ? -amount() : 0, axis == 2 ? -amount() : 0));
			small(host, bx + 70, y + v(46) + 1, 16, "+", () -> step(host, axis == 0 ? amount() : 0, axis == 1 ? amount() : 0, axis == 2 ? amount() : 0));
		}

		String[] turns = {"0°", "90°", "180°", "270°"};
		for (int i = 0; i < 4; i++) {
			int quarter = i;
			boolean current = hologram.quarterTurns() == i;
			small(host, x + PAD + i * 66, y + v(90), 62, current ? "[" + turns[i] + "]" : turns[i], () -> orient(host, quarter, hologram.mirrored()));
		}

		Component[] mirrors = {Component.translatable("holoprint.placement.mirror.none"), Component.translatable("holoprint.placement.mirror.left_right"),
			Component.translatable("holoprint.placement.mirror.front_back")};
		for (int i = 0; i < 3; i++) {
			int which = i;
			boolean current = (i == 0 && !hologram.mirrored()) || (i == 1 && hologram.mirrored());
			Component label = current ? Component.literal("[").append(mirrors[i]).append("]") : mirrors[i];
			host.add(Button.builder(label, b -> {
				switch (which) {
					case 0 -> orient(host, hologram.quarterTurns(), false);
					case 1 -> orient(host, hologram.quarterTurns(), true);
					// Mirrored front to back is the left-right mirror turned half a circle.
					default -> orient(host, (hologram.quarterTurns() + 2) & 3, !hologram.mirrored());
				}
			}).bounds(x + PAD + i * 100, y + v(130), 96, 16).build());
		}

		state(host, x + PAD, y + v(158), "holoprint.placement.enabled", hologram.enabled(), () -> hologram.enabled(!hologram.enabled()));
		state(host, x + PAD + 88, y + v(158), "holoprint.placement.visible", hologram.visible(), () -> hologram.visible(!hologram.visible()));
		state(host, x + PAD + 176, y + v(158), "holoprint.placement.locked", hologram.locked(), () -> hologram.locked(!hologram.locked()));
		state(host, x + PAD + 264, y + v(158), "holoprint.placement.entities", hologram.showEntities(), () -> hologram.showEntities(!hologram.showEntities()));

		button(host, x + PAD, y + v(188), 100, Component.translatable("gui.back"), host::back);
		button(host, x + WIDTH - PAD - 100, y + v(188), 100, Component.translatable("holoprint.placements.remove"), () -> {
			if (!hologram.locked()) {
				HologramRenderer.remove(hologram);
				Editor.activePlacementChanged(Minecraft.getInstance());
				host.back();
			}
		});
	}

	private static void state(Host host, int x, int y, String key, boolean on, Runnable toggle) {
		Component value = Component.translatable(on ? "options.on" : "options.off");
		host.add(Button.builder(Component.translatable(key, value), b -> {
			toggle.run();
			host.rebuild();
		}).bounds(x, y, 84, 20).build());
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		text(graphics, Component.translatable("holoprint.placement.name"), x + PAD, y + 5, MUTED);
		text(graphics, Component.translatable("holoprint.placement.origin"), x + PAD, y + v(32), MUTED);
		for (int i = 0; i < 3; i++) {
			text(graphics, AXES[i], x + COLUMNS[i] + 2, y + v(46) + 5, MUTED);
		}
		text(graphics, Component.translatable("holoprint.placement.rotation"), x + PAD, y + v(76), MUTED);
		text(graphics, Component.translatable("holoprint.placement.mirror"), x + PAD, y + v(116), MUTED);
	}
}
