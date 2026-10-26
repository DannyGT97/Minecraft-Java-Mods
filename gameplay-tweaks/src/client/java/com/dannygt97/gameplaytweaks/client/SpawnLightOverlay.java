package com.dannygt97.gameplaytweaks.client;

import com.dannygt97.gameplaytweaks.client.config.GameplayTweaksConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

/**
 * A mesh on the ground around the player that says whether hostile mobs can spawn there: red where they can (dark enough), green where the light
 * keeps them away. Only the spots a mob could stand on are marked. The light rule is the one of the dimension (in the Overworld: no block light at
 * all and little enough sky light), so the marks follow the time of day and every torch placed.
 */
public final class SpawnLightOverlay {
	private static final int RADIUS = 12;
	private static final int DOWN = 4;
	private static final int UP = 4;
	private static final int REFRESH_TICKS = 10;
	private static final float LINE_WIDTH = 2.0F;
	private static final int RED = 0xFFFF3030;
	private static final int GREEN = 0xFF30E050;

	private static volatile long[] dark = new long[0];
	private static volatile long[] lit = new long[0];
	private static int ticks;

	private SpawnLightOverlay() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SpawnLightOverlay::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(SpawnLightOverlay::render);
	}

	private static void tick(Minecraft minecraft) {
		if (!GameplayTweaksConfig.isLightMeshEnabled() || minecraft.level == null || minecraft.player == null) {
			dark = new long[0];
			lit = new long[0];
			ticks = 0;
			return;
		}
		if (ticks++ % REFRESH_TICKS != 0) {
			return;
		}
		ClientLevel level = minecraft.level;
		DimensionType dimension = level.dimensionType();
		int blockLimit = dimension.monsterSpawnBlockLightLimit();
		int skyLimit = dimension.monsterSpawnLightTest().maxInclusive();
		BlockPos centre = minecraft.player.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		LongArrayList red = new LongArrayList();
		LongArrayList green = new LongArrayList();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) {
					continue;
				}
				for (int dy = -DOWN; dy <= UP; dy++) {
					pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (!canStand(level, pos)) {
						continue;
					}
					boolean spawns = level.getBrightness(LightLayer.BLOCK, pos) <= blockLimit && level.getMaxLocalRawBrightness(pos) <= skyLimit;
					(spawns ? red : green).add(pos.asLong());
				}
			}
		}
		dark = red.toLongArray();
		lit = green.toLongArray();
	}

	/** A spot a hostile mob could spawn in: room for its body (two blocks of air) over a block that can hold it up. */
	private static boolean canStand(ClientLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		var ground = level.getBlockState(below);
		if (!ground.isFaceSturdy(level, below, Direction.UP) || !ground.isValidSpawn(level, below, EntityTypes.ZOMBIE)) {
			return false;
		}
		return free(level, pos) && free(level, pos.above());
	}

	private static boolean free(ClientLevel level, BlockPos pos) {
		var state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	private static void render(LevelRenderContext context) {
		if (!GameplayTweaksConfig.isLightMeshEnabled()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		int alpha = Math.round(255 * GameplayTweaksConfig.getLightMeshOpacity() / 100.0F) << 24;
		draw(context, dark, alpha | (RED & 0xFFFFFF), camera);
		draw(context, lit, alpha | (GREEN & 0xFFFFFF), camera);
	}

	private static void draw(LevelRenderContext context, long[] spots, int colour, Vec3 camera) {
		if (spots.length == 0) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.linesTranslucent(), (pose, consumer) -> {
			for (long packed : spots) {
				float x1 = (float) (BlockPos.getX(packed) - camera.x) + 0.1F, x2 = x1 + 0.8F;
				float y = (float) (BlockPos.getY(packed) - camera.y) + 0.02F;
				float z1 = (float) (BlockPos.getZ(packed) - camera.z) + 0.1F, z2 = z1 + 0.8F;
				// the outline of the square and its two diagonals
				float[][] lines = {{x1, z1, x2, z1}, {x2, z1, x2, z2}, {x2, z2, x1, z2}, {x1, z2, x1, z1}, {x1, z1, x2, z2}, {x2, z1, x1, z2}};
				for (float[] line : lines) {
					float nx = line[2] - line[0], nz = line[3] - line[1];
					float length = (float) Math.sqrt(nx * nx + nz * nz);
					nx /= length;
					nz /= length;
					consumer.addVertex(pose, line[0], y, line[1]).setColor(colour).setNormal(pose, nx, 0.0F, nz).setLineWidth(LINE_WIDTH);
					consumer.addVertex(pose, line[2], y, line[3]).setColor(colour).setNormal(pose, nx, 0.0F, nz).setLineWidth(LINE_WIDTH);
				}
			}
		});
	}
}
