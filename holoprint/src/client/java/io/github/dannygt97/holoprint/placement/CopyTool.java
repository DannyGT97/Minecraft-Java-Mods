package io.github.dannygt97.holoprint.placement;

import java.util.List;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.selection.Clipboard;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Copy mode: a click on a hologram picks up a copy of it, which then follows the pointer; the next
 * click puts that copy down where it is and it stays there. Only one copy is made per pick: to make
 * another, click a hologram again. Ctrl+V starts the same thing from the clipboard.
 */
final class CopyTool implements EditorTool {
	private static final double PICK_DISTANCE = 256.0;
	/** How far in front of the camera a pasted hologram first appears, besides half of its own width. */
	private static final double PASTE_DISTANCE = 6.0;

	private final FollowCursor follow = new FollowCursor();
	private Hologram carried;
	/** When the copy in hand is the real area picked up to be moved with /clone: where the area is and how big it is. */
	private BlockPos moveFrom;
	private int moveX, moveY, moveZ;

	/** Starts carrying {@code hologram} (a copy of what was copied). */
	void carry(Minecraft mc, Hologram hologram) {
		drop();
		carried = hologram;
		HologramRenderer.add(carried);
		follow.reset(carried);
	}

	/** Picks up the real area at {@code from} to move it: {@code hologram} starts over it and a click moves the blocks to where it is put. */
	void carryMove(Minecraft mc, Hologram hologram, BlockPos from, int sizeX, int sizeY, int sizeZ) {
		carry(mc, hologram);
		moveFrom = from;
		moveX = sizeX;
		moveY = sizeY;
		moveZ = sizeZ;
	}

	/** Starts carrying a hologram that has no place yet: it appears in front of the free camera and follows the pointer from there. */
	void carryFromCamera(Minecraft mc, Hologram hologram) {
		Vec3 eye = Freecam.eyePosition();
		Vec3 look = Freecam.lookVector();
		double distance = PASTE_DISTANCE + Math.max(hologram.sizeX(), hologram.sizeZ()) / 2.0;
		BlockPos target = BlockPos.containing(eye.add(look.scale(distance)));
		hologram.placeCentered(Editor.groundNear(mc, target));
		carry(mc, hologram);
	}

	/** Stops carrying and removes the copy that was in hand (nothing was put down). */
	private void drop() {
		moveFrom = null;
		if (carried != null) {
			HologramRenderer.remove(carried);
			carried = null;
		}
	}

	@Override
	public void enter(Minecraft mc, boolean fresh) {}

	@Override
	public void leave(Minecraft mc) {
		drop();
	}

	@Override
	public void tick(Minecraft mc, Vec3 ray) {
		if (carried == null) {
			return;
		}
		follow.update(mc, carried, ray, true);
	}

	@Override
	public void primaryClick(Minecraft mc, Vec3 ray) {
		if (carried != null && moveFrom != null) {
			// The real area is moved here. If the command could not be started (turned copy, no permission) the area stays in hand.
			if (SelectionActions.cloneMove(mc, moveFrom, moveX, moveY, moveZ, carried)) {
				drop();
				io.github.dannygt97.holoprint.ui.HotbarTools.released(true);
			}
			return;
		}
		if (carried != null) {
			// The copy in hand is put down here and stays. It becomes the placement that Move works on, and
			// nothing is carried any more, so a further click does not make another copy of it.
			Hologram placed = carried;
			carried = null;
			HologramRenderer.makeActive(placed);
			io.github.dannygt97.holoprint.ui.HotbarTools.released(true);
			return;
		}
		if (ray == null) {
			return;
		}
		Hologram under = HologramRenderer.pick(Freecam.eyePosition(), ray, PICK_DISTANCE, null);
		if (under == null) {
			Editor.message(mc, Component.translatable("holoprint.message.click_a_hologram"));
			return;
		}
		Clipboard.set(under);
		carry(mc, under.copy());
	}

	/** Right click lets go of the copy in hand (it is removed); with nothing in hand it leaves the editor. */
	@Override
	public void secondaryClick(Minecraft mc, Vec3 ray) {
		if (carried != null) {
			drop();
			io.github.dannygt97.holoprint.ui.HotbarTools.released(false);
		} else {
			Editor.stop(mc);
		}
	}

	@Override
	public void cancel(Minecraft mc) {
		drop();
	}

	@Override
	public boolean escape(Minecraft mc) {
		if (carried == null) {
			return false;
		}
		drop();
		return true;
	}

	@Override
	public List<Component> legend() {
		if (carried == null) {
			return List.of(EditorHud.line("holoprint.hud.copy.pick"),
				EditorHud.line("holoprint.hud.copy.paste", EditorHud.ctrl(EditorKeys.PASTE)));
		}
		if (moveFrom != null) {
			return List.of(EditorHud.line("holoprint.hud.copy.moving"),
				EditorHud.line("holoprint.hud.move.turn", EditorHud.name(EditorKeys.ROTATE), EditorHud.name(EditorKeys.MIRROR)),
				EditorHud.line("holoprint.hud.move.nudge", EditorHud.name(EditorKeys.FORWARD), EditorHud.name(EditorKeys.UP)),
				EditorHud.line("holoprint.hud.copy.stop", EditorHud.name(EditorKeys.CANCEL)));
		}
		return List.of(EditorHud.line("holoprint.hud.copy.carrying"),
			EditorHud.line("holoprint.hud.move.turn", EditorHud.name(EditorKeys.ROTATE), EditorHud.name(EditorKeys.MIRROR)),
			EditorHud.line("holoprint.hud.move.nudge", EditorHud.name(EditorKeys.FORWARD), EditorHud.name(EditorKeys.UP)),
			EditorHud.line("holoprint.hud.copy.stop", EditorHud.name(EditorKeys.CANCEL)));
	}
}
