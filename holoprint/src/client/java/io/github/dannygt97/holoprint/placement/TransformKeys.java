package io.github.dannygt97.holoprint.placement;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.Hologram;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Rotate, mirror and one-block nudges, shared by every tool that holds a hologram. */
final class TransformKeys {
	private TransformKeys() {}

	/**
	 * Applies the key presses to {@code hologram}. While the hologram {@code follows} the pointer the
	 * nudges accumulate in the returned offset; otherwise they move the hologram itself.
	 */
	static BlockPos apply(Hologram hologram, boolean follows, BlockPos offset) {
		while (EditorKeys.ROTATE.consumeClick()) hologram.rotateClockwise();
		while (EditorKeys.MIRROR.consumeClick()) hologram.mirror();
		BlockPos result = offset;
		result = nudge(hologram, follows, result, EditorKeys.FORWARD, 0, 0);
		result = nudge(hologram, follows, result, EditorKeys.BACK, 2, 0);
		result = nudge(hologram, follows, result, EditorKeys.LEFT, 3, 0);
		result = nudge(hologram, follows, result, EditorKeys.RIGHT, 1, 0);
		result = nudge(hologram, follows, result, EditorKeys.UP, 0, 1);
		result = nudge(hologram, follows, result, EditorKeys.DOWN, 0, -1);
		return result;
	}

	/**
	 * {@code quarter} is the direction relative to where the camera faces (0 forward, 1 right, 2 back,
	 * 3 left) and {@code vertical} is +1 up, -1 down.
	 */
	private static BlockPos nudge(Hologram hologram, boolean follows, BlockPos offset, KeyMapping key, int quarter, int vertical) {
		BlockPos result = offset;
		while (key.consumeClick()) {
			BlockPos step;
			if (vertical != 0) {
				step = new BlockPos(0, vertical, 0);
			} else {
				Direction facing = Direction.fromYRot(Freecam.yaw());
				for (int i = 0; i < quarter; i++) {
					facing = facing.getClockWise();
				}
				step = new BlockPos(facing.getStepX(), 0, facing.getStepZ());
			}
			if (follows) {
				result = result.offset(step);
			} else {
				hologram.moveBy(step.getX(), step.getY(), step.getZ());
			}
		}
		return result;
	}
}
