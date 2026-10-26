package com.dannygt97.gameplaytweaks.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

/**
 * Holoprint's assisted placement decides where blocks go in and around a hologram. Reach-around bridging stays out of those spots, so it never puts
 * a block of its own where the hologram does not ask for one. Without Holoprint installed (or an older version of it) nothing is held back.
 */
final class HoloprintCompat {
	private static final MethodHandle OWNS = find();

	private HoloprintCompat() {}

	private static MethodHandle find() {
		if (!FabricLoader.getInstance().isModLoaded("holoprint")) {
			return null;
		}
		try {
			Class<?> placement = Class.forName("io.github.dannygt97.holoprint.placement.EasyPlace");
			return MethodHandles.publicLookup().findStatic(placement, "owns", MethodType.methodType(boolean.class, BlockPos.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	/** True when Holoprint's assisted placement looks after {@code pos} (it is in or next to a visible hologram). */
	static boolean owns(BlockPos pos) {
		if (OWNS == null) {
			return false;
		}
		try {
			return (boolean) OWNS.invokeExact(pos);
		} catch (Throwable e) {
			return false;
		}
	}
}
