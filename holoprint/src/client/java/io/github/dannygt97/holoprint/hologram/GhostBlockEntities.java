package io.github.dannygt97.holoprint.hologram;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The blocks of a schematic that are drawn by the game as block entities, because they have no plain model:
 * chests, signs with their text, banners, heads, beds, shulker boxes and the like. They are real block
 * entity objects built from the saved data and never added to the world, so nothing ticks and nothing
 * opens; each frame they are extracted from the same resting state and drawn at the hologram's place.
 */
public final class GhostBlockEntities {
	/** The most block entities drawn in one frame. */
	private static final int MAX_PER_FRAME = 400;

	/** A block entity of the hologram, waiting to be created: where it is in the hologram's own coordinates, its block and its saved data. */
	record Definition(BlockPos local, BlockState state, CompoundTag data) {}

	/** A created block entity and where it is relative to the hologram's origin. */
	record Ghost(BlockEntity entity, BlockPos local) {}

	private GhostBlockEntities() {}

	/** True when the block of this state is drawn by a block entity renderer, so its data is worth keeping. */
	static boolean isBlockEntity(BlockState state) {
		return state.getBlock() instanceof EntityBlock;
	}

	/** Creates the block entities of {@code definitions}. Ones this version cannot build are left out. */
	static List<Ghost> create(Minecraft mc, List<Definition> definitions, BlockPos origin) {
		List<Ghost> out = new ArrayList<>();
		if (mc.level == null) {
			return out;
		}
		for (Definition definition : definitions) {
			try {
				CompoundTag data = definition.data().copy();
				BlockEntity entity = BlockEntity.loadStatic(origin.offset(definition.local()), definition.state(), data, mc.level.registryAccess());
				if (entity == null) {
					continue;
				}
				// It stands at its real place in the world (the renderers cull by distance), and the level is only given to it, never told about it.
				entity.setLevel(mc.level);
				out.add(new Ghost(entity, definition.local()));
			} catch (RuntimeException ignored) {
				// A block entity this version cannot build is simply not shown.
			}
		}
		return out;
	}

	/** Submits the block entities of one hologram that are in range and still to do. Returns how many were drawn. */
	static int render(Minecraft mc, Hologram hologram, List<Ghost> ghosts, Vec3 camera, double range, CameraRenderState cameraState,
			PoseStack poseStack, SubmitNodeCollector collector, java.util.BitSet built, int argb, int budget) {
		// What the renderers submit is made translucent like the rest of the hologram.
		SubmitNodeCollector translucent = TranslucentCollector.wrap(collector, argb);
		BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
		int drawn = 0;
		for (Ghost ghost : ghosts) {
			if (drawn >= Math.min(budget, MAX_PER_FRAME)) {
				break;
			}
			BlockPos local = ghost.local();
			if (!LayerView.shows(local.getY())) {
				continue;
			}
			if (built != null && built.get((local.getY() * hologram.sizeZ() + local.getZ()) * hologram.sizeX() + local.getX())) {
				continue;
			}
			BlockPos at = hologram.origin().offset(local);
			double dx = at.getX() - camera.x, dy = at.getY() - camera.y, dz = at.getZ() - camera.z;
			if (dx * dx + dy * dy + dz * dz > range * range) {
				continue;
			}
			try {
				BlockEntityRenderState state = dispatcher.tryExtractRenderState(ghost.entity(), 0.0F, null, false);
				if (state == null) {
					state = dispatcher.tryExtractRenderState(ghost.entity(), 0.0F, null, true);
				}
				if (state == null) {
					continue;
				}
				state.blockPos = at;
				state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
				poseStack.pushPose();
				poseStack.translate(dx, dy, dz);
				try {
					dispatcher.submit(state, poseStack, translucent, cameraState);
				} finally {
					poseStack.popPose();
				}
				drawn++;
			} catch (RuntimeException ignored) {
				// One block entity whose renderer fails must not take the rest of the frame with it.
			}
		}
		return drawn;
	}
}
