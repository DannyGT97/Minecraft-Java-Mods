package io.github.dannygt97.holoprint.selection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * The area being selected: two corner blocks. The first click sets the first corner and, until the
 * second click, the second corner follows the pointer so the box can be seen growing.
 */
public final class Selection {
	private static BlockPos first;
	private static BlockPos second;
	private static boolean complete;
	/** A name the user gave the area in the area editor; used when it is saved. */
	private static String name = "";

	private Selection() {}

	public static String name() {
		return name;
	}

	public static void name(String value) {
		name = value == null ? "" : value;
	}

	public static void clear() {
		first = null;
		second = null;
		complete = false;
		name = "";
	}

	public static boolean isEmpty() {
		return first == null;
	}

	/** True once both corners are fixed. */
	public static boolean isComplete() {
		return complete;
	}

	/** Sets the first corner and starts a new selection. */
	public static void begin(BlockPos corner) {
		first = corner;
		second = corner;
		complete = false;
	}

	/** Moves the second corner without fixing it, while it follows the pointer. */
	public static void preview(BlockPos corner) {
		if (first != null && !complete) {
			second = corner;
		}
	}

	/** Fixes the second corner. */
	public static void finish(BlockPos corner) {
		if (first != null) {
			second = corner;
			complete = true;
		}
	}

	/** The corner that was clicked first, or null. */
	public static BlockPos first() {
		return first;
	}

	/** The corner that was clicked second (or is following the pointer), or null. */
	public static BlockPos second() {
		return second;
	}

	/** Moves a corner by hand from the area editor; a selection that did not exist yet is created. */
	public static void setCorner(int which, BlockPos corner) {
		if (first == null) {
			first = corner;
			second = corner;
		}
		if (which == 0) {
			first = corner;
		} else {
			second = corner;
		}
		complete = true;
	}

	public static void swapCorners() {
		if (first != null) {
			BlockPos keep = first;
			first = second;
			second = keep;
		}
	}

	/**
	 * Moves one face of the box outwards (positive amount) or inwards (negative); the box never gets
	 * thinner than one block.
	 */
	public static void moveFace(net.minecraft.core.Direction face, int amount) {
		if (first == null) {
			return;
		}
		BlockPos lo = min();
		BlockPos hi = max();
		int axisLo = lo.get(face.getAxis());
		int axisHi = hi.get(face.getAxis());
		if (face.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE) {
			axisHi = Math.max(axisLo, axisHi + amount);
		} else {
			axisLo = Math.min(axisHi, axisLo - amount);
		}
		lo = with(lo, face.getAxis(), axisLo);
		hi = with(hi, face.getAxis(), axisHi);
		first = lo;
		second = hi;
		complete = true;
	}

	private static BlockPos with(BlockPos pos, net.minecraft.core.Direction.Axis axis, int value) {
		return switch (axis) {
			case X -> new BlockPos(value, pos.getY(), pos.getZ());
			case Y -> new BlockPos(pos.getX(), value, pos.getZ());
			case Z -> new BlockPos(pos.getX(), pos.getY(), value);
		};
	}

	public static BlockPos min() {
		return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
	}

	public static BlockPos max() {
		return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
	}

	public static int sizeX() { return max().getX() - min().getX() + 1; }
	public static int sizeY() { return max().getY() - min().getY() + 1; }
	public static int sizeZ() { return max().getZ() - min().getZ() + 1; }

	public static long volume() {
		return isEmpty() ? 0 : (long) sizeX() * sizeY() * sizeZ();
	}

	/** The box that covers every selected block, or null when nothing is selected. */
	public static AABB box() {
		if (first == null) {
			return null;
		}
		BlockPos lo = min();
		BlockPos hi = max();
		return new AABB(lo.getX(), lo.getY(), lo.getZ(), hi.getX() + 1, hi.getY() + 1, hi.getZ() + 1);
	}
}
