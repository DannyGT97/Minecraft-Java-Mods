package io.github.dannygt97.holoprint.placement;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.Hologram;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Makes a hologram follow the pointer with its centre under the cross-hair. It keeps the distance
 * from the camera it had when it started (the pointer swings it around the camera, it does not run
 * off to whatever terrain lies behind it), settles on terrain that is nearer than that distance and
 * on ground it hovers just above, and otherwise hangs in the air centred on the pointer. Whenever its
 * target jumps (picked up from the side, settling on the ground) it glides there over a few ticks
 * instead of teleporting.
 */
final class FollowCursor {
	private static final double MIN_DISTANCE = 4.0;
	private static final double MAX_DISTANCE = 200.0;
	/** Share of the remaining distance covered each tick while gliding to the target. */
	private static final double GLIDE = 0.55;

	private double distance = 10.0;
	/** The player's nudges, added to the target. */
	private BlockPos nudge = BlockPos.ZERO;

	/** Starts following from where {@code hologram} is now. */
	void reset(Hologram hologram) {
		BlockPos o = hologram.origin();
		Vec3 centre = new Vec3(o.getX() + hologram.sizeX() / 2.0, o.getY() + hologram.sizeY() / 2.0, o.getZ() + hologram.sizeZ() / 2.0);
		distance = Mth.clamp(Freecam.eyePosition().distanceTo(centre), MIN_DISTANCE, MAX_DISTANCE);
		nudge = BlockPos.ZERO;
	}

	/** One tick: applies the rotate, mirror and nudge keys and, while {@code follows}, moves the hologram with the pointer. */
	void update(Minecraft mc, Hologram hologram, Vec3 ray, boolean follows) {
		nudge = TransformKeys.apply(hologram, follows, nudge);
		if (!follows || ray == null) {
			return;
		}
		Editor.Aim aim = Editor.aimHeld(mc, ray, distance);
		if (aim.grounded() && aim.distance() > distance + 1.0) {
			// It settled on terrain further away than it was held: it is held there from now on.
			distance = Mth.clamp(aim.distance(), MIN_DISTANCE, MAX_DISTANCE);
		}
		int x = aim.pos().getX() - hologram.sizeX() / 2 + nudge.getX();
		int z = aim.pos().getZ() - hologram.sizeZ() / 2 + nudge.getZ();
		// On the ground its bottom rests there; in the air its middle is on the cross-hair.
		int y = (aim.grounded() ? aim.pos().getY() : aim.pos().getY() - hologram.sizeY() / 2) + nudge.getY();
		BlockPos now = hologram.origin();
		double dx = x - now.getX(), dy = y - now.getY(), dz = z - now.getZ();
		if (dx * dx + dy * dy + dz * dz <= 2.0) {
			hologram.moveTo(new BlockPos(x, y, z));
		} else {
			hologram.moveTo(new BlockPos(now.getX() + (int) Math.round(dx * GLIDE), now.getY() + (int) Math.round(dy * GLIDE), now.getZ() + (int) Math.round(dz * GLIDE)));
		}
	}
}
