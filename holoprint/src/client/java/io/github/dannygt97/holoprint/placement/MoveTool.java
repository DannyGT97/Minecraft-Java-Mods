package io.github.dannygt97.holoprint.placement;

import java.util.List;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Move mode: the hologram follows where the pointer aims; a left click pins it so it can be looked at from
 * any side and another click picks it up again. Only that left click confirms a move: if a placed
 * hologram has been picked up and the player leaves without confirming (right click, cancel, another
 * mode, closing the editor, picking another placement), it goes back where it was.
 */
final class MoveTool implements EditorTool {
	private static final double PICK_DISTANCE = 256.0;

	private enum State { FOLLOW, PINNED }

	private State state = State.FOLLOW;
	private BlockPos savedOrigin;
	private int savedTurns;
	private boolean savedMirror;
	private final FollowCursor follow = new FollowCursor();
	/** The hologram being carried right now, if any, and whether it goes back to where it was unless the move is confirmed. */
	private Hologram carried;
	private boolean restorable;

	@Override
	public void enter(Minecraft mc, boolean fresh) {
		// Another placement was chosen while one was being carried: the carried one is not moved after all.
		putBack();
		Hologram hologram = HologramRenderer.active();
		if (hologram == null) {
			return;
		}
		savedOrigin = hologram.origin();
		savedTurns = hologram.quarterTurns();
		savedMirror = hologram.mirrored();
		// A new placement starts where it was put, in front of the camera, and follows from there.
		follow.reset(hologram);
		state = fresh ? State.FOLLOW : State.PINNED;
		// A new placement has no earlier place to go back to; it stays where it is left.
		carried = fresh ? hologram : null;
		restorable = false;
	}

	@Override
	public void leave(Minecraft mc) {
		putBack();
		state = State.PINNED;
	}

	/** If a placed hologram is being carried and the move was not confirmed, puts it back where it was picked up. */
	private void putBack() {
		if (state == State.FOLLOW && carried != null && restorable && savedOrigin != null) {
			carried.setTransform(savedOrigin, savedTurns, savedMirror);
		}
		carried = null;
		restorable = false;
	}

	@Override
	public void tick(Minecraft mc, Vec3 ray) {
		Hologram hologram = HologramRenderer.active();
		if (hologram == null) {
			Editor.setMode(mc, EditorMode.SELECT);
			return;
		}
		if (hologram.locked()) {
			// A locked placement stays where it is (the unused key presses are thrown away by the editor).
			return;
		}
		// With a free cursor the hologram stays put while the pointer is over a button.
		follow.update(mc, hologram, ray, state == State.FOLLOW);
	}

	@Override
	public void primaryClick(Minecraft mc, Vec3 ray) {
		if (state == State.FOLLOW) {
			// It is being carried: put it down where it is. This left click is what confirms the move.
			state = State.PINNED;
			carried = null;
			restorable = false;
			return;
		}
		if (ray == null) {
			return;
		}
		// Nothing is carried: the hologram the pointer is over is the one that gets picked up, whichever
		// placement was used last.
		Hologram under = HologramRenderer.pick(Freecam.eyePosition(), ray, PICK_DISTANCE, null);
		if (under == null) {
			Editor.message(mc, Component.translatable("holoprint.message.click_a_hologram"));
			return;
		}
		if (under.locked()) {
			Editor.message(mc, Component.translatable("holoprint.message.placement_locked"));
			return;
		}
		HologramRenderer.makeActive(under);
		savedOrigin = under.origin();
		savedTurns = under.quarterTurns();
		savedMirror = under.mirrored();
		// It stays where it is and then follows the pointer from there, without jumping to it.
		follow.reset(under);
		state = State.FOLLOW;
		carried = under;
		restorable = true;
	}

	@Override
	public void secondaryClick(Minecraft mc, Vec3 ray) {
		Editor.stop(mc);
	}

	/** Puts the hologram back where it was when the mode began, and leaves the editor. */
	@Override
	public void cancel(Minecraft mc) {
		Hologram hologram = HologramRenderer.active();
		if (hologram != null && savedOrigin != null) {
			hologram.setTransform(savedOrigin, savedTurns, savedMirror);
		}
		carried = null;
		restorable = false;
		Editor.stop(mc);
	}

	@Override
	public List<Component> legend() {
		boolean pinned = state == State.PINNED;
		return List.of(
			Component.translatable(pinned ? "holoprint.hud.move.pinned" : "holoprint.hud.move.following"),
			Component.translatable("holoprint.hud.move.turn", EditorHud.name(EditorKeys.ROTATE), EditorHud.name(EditorKeys.MIRROR)),
			Component.translatable("holoprint.hud.move.nudge", EditorHud.name(EditorKeys.FORWARD), EditorHud.name(EditorKeys.UP)),
			Component.translatable("holoprint.hud.move.done", EditorHud.name(EditorKeys.CANCEL)),
			Component.translatable("holoprint.hud.move.build", EditorHud.name(EditorKeys.BUILD)));
	}
}
