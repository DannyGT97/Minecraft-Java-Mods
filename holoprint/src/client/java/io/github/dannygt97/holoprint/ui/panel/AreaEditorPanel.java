package io.github.dannygt97.holoprint.ui.panel;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.placement.Editor;
import io.github.dannygt97.holoprint.placement.SelectionActions;
import io.github.dannygt97.holoprint.selection.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * Fine adjustment of the selected area without leaving the editor, laid out like Litematica's area
 * editor: the name of the selection with a Set button, the two corners side by side with a box for
 * every coordinate (the number can be typed, or changed with - and + where Shift moves 8 blocks), a
 * "Move to player" button under each corner, and a button to save the area as a schematic. Below,
 * each face of the box can be pushed outwards or pulled inwards.
 */
public final class AreaEditorPanel extends EditorPanel {
	private static final int NOMINAL_WIDTH = 436;
	private final int WIDTH = Math.min(NOMINAL_WIDTH, availableWidth());
	private static final int COLUMN = 212;
	private static final String[] AXES = {"X", "Y", "Z"};
	private static final Direction[] FACES = {Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};
	private static final int NOMINAL = 236;

	private final float scale = fit(NOMINAL);

	private int v(int value) {
		return Math.round(value * scale);
	}

	@Override
	public Component title() {
		return Component.translatable("holoprint.panel.area_editor");
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public int height() {
		return 26 + v(NOMINAL);
	}

	private static int amount() {
		return Minecraft.getInstance().hasShiftDown() ? 8 : 1;
	}

	private static BlockPos corner(int which) {
		return which == 0 ? Selection.first() : Selection.second();
	}

	/** The corner as it is now; with no selection yet, the camera location, so the boxes always show a number. */
	private static BlockPos base(int which) {
		BlockPos c = corner(which);
		return c != null ? c : location();
	}

	/** Where the free camera is, rounded to the block; the player's own block when the camera is not flying. */
	private static void setCorner(int which, BlockPos corner) {
		if (Selection.isEmpty()) {
			Editor.beginSelection(Minecraft.getInstance());
		}
		Selection.setCorner(which, corner);
	}

	private static BlockPos location() {
		return Freecam.isActive() ? BlockPos.containing(Freecam.eyePosition()) : Minecraft.getInstance().player.blockPosition();
	}

	private static BlockPos with(BlockPos pos, int axis, int value) {
		return new BlockPos(axis == 0 ? value : pos.getX(), axis == 1 ? value : pos.getY(), axis == 2 ? value : pos.getZ());
	}

	private static int get(BlockPos pos, int axis) {
		return axis == 0 ? pos.getX() : axis == 1 ? pos.getY() : pos.getZ();
	}

	@Override
	public void init(Host host, int x, int y) {
		boolean has = !Selection.isEmpty();
		box(host, x + PAD + 64, y, WIDTH - 2 * PAD - 64 - 50, Selection.name().isBlank()
			? Component.translatable("holoprint.area.default_name").getString() : Selection.name()).setResponder(Selection::name);
		button(host, x + WIDTH - PAD - 46, y - 1, 46, Component.translatable("holoprint.area.set"), host::rebuild);

		for (int which = 0; which < 2; which++) {
			int cx = x + PAD + which * COLUMN;
			int w = which;
			for (int axis = 0; axis < 3; axis++) {
				int a = axis;
				int ry = y + v(48) + axis * v(24);
				{
					EditBox coordinate = box(host, cx + 14, ry, 84, String.valueOf(get(base(w), a)));
					coordinate.setMaxLength(9);
					// Typing a whole number moves that coordinate at once; half-typed text (like "-") is left alone.
					coordinate.setResponder(text -> {
						try {
							setCorner(w, with(base(w), a, Integer.parseInt(text.trim())));
						} catch (NumberFormatException ignored) {
							// not a number yet
						}
					});
					small(host, cx + 102, ry + 1, 16, "-", () -> {
						setCorner(w, with(base(w), a, get(base(w), a) - amount()));
						host.rebuild();
					});
					small(host, cx + 120, ry + 1, 16, "+", () -> {
						setCorner(w, with(base(w), a, get(base(w), a) + amount()));
						host.rebuild();
					});
				}
			}
			button(host, cx, y + v(48) + 3 * v(24) + 2, 138, Component.translatable("holoprint.area.move_to_player"), () -> {
				setCorner(w, location());
				host.rebuild();
			});
		}
		if (has) {
			for (int i = 0; i < FACES.length; i++) {
				Direction face = FACES[i];
				int fx = x + PAD + (i % 3) * 140;
				int fy = y + v(172) + (i / 3) * v(22);
				small(host, fx + 56, fy, 16, "-", () -> {
					Selection.moveFace(face, -amount());
					host.rebuild();
				});
				small(host, fx + 76, fy, 16, "+", () -> {
					Selection.moveFace(face, amount());
					host.rebuild();
				});
			}
		}
		// Back, Clear and the actions that used to live in the toolbar: one row of equal buttons.
		Minecraft mc = Minecraft.getInstance();
		int gap = 4;
		int w = (WIDTH - 2 * PAD - 5 * gap) / 6;
		int by = y + v(214);
		button(host, x + PAD, by, w, Component.translatable("gui.back"), host::back);
		button(host, x + PAD + (w + gap), by, w, Component.translatable("holoprint.area.btn.clear"), () -> {
			Selection.clear();
			host.rebuild();
		});
		button(host, x + PAD + 2 * (w + gap), by, w, Component.translatable("holoprint.area.btn.copy"), () -> SelectionActions.copy(mc));
		button(host, x + PAD + 3 * (w + gap), by, w, Component.translatable("holoprint.area.btn.cut"), () -> SelectionActions.cut(mc)).active = SelectionActions.canChangeWorld(mc);
		button(host, x + PAD + 4 * (w + gap), by, w, Component.translatable("holoprint.area.btn.delete"), () -> SelectionActions.delete(mc)).active = SelectionActions.canChangeWorld(mc);
		button(host, x + PAD + 5 * (w + gap), by, w, Component.translatable("holoprint.area.btn.save"), () -> SelectionActions.save(mc));
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		boolean has = !Selection.isEmpty();
		text(graphics, Component.translatable("holoprint.area.name"), x + PAD, y + 5, MUTED);
		for (int which = 0; which < 2; which++) {
			int cx = x + PAD + which * COLUMN;
			text(graphics, Component.translatable("holoprint.area.corner", which + 1), cx, y + v(32), GOLD);
			for (int axis = 0; axis < 3; axis++) {
				text(graphics, AXES[axis], cx + 2, y + v(48) + axis * v(24) + 5, MUTED);
			}
		}
		Component size = !has ? Component.translatable("holoprint.area.no_selection")
			: Component.translatable("holoprint.hud.select.size", Selection.sizeX(), Selection.sizeY(), Selection.sizeZ(), Selection.volume());
		text(graphics, size, x + PAD, y + v(150), TEXT);
		text(graphics, Component.translatable("holoprint.area.shift"), x + WIDTH - PAD - font().width(Component.translatable("holoprint.area.shift")), y + v(150), MUTED);
		if (has) {
			String[] names = {"west", "east", "down", "up", "north", "south"};
			for (int i = 0; i < FACES.length; i++) {
				text(graphics, Component.translatable("holoprint.area.face." + names[i]), x + PAD + (i % 3) * 140, y + v(176) + (i / 3) * v(22), TEXT);
			}
		}
	}
}
