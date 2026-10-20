package io.github.dannygt97.holoprint.hologram;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * The entities of a schematic (armor stands, item frames, animals...) drawn as frozen figures. They
 * are real entity objects that are never added to the world and never ticked, so nothing moves, no
 * AI runs and no animation plays: every frame is extracted from the same resting state.
 */
public final class GhostEntities {
	/** While a hologram entity is being drawn: the ARGB it is drawn with (opacity and tint of the hologram); 0 otherwise. */
	public static volatile int ghostColor;
	/** The most entities drawn in one frame, nearest first is not needed at this size. */
	private static final int MAX_PER_FRAME = 160;

	/** An entity of the hologram, waiting to be created: its saved data and where it stands, in the hologram's own coordinates. */
	record Definition(net.minecraft.nbt.CompoundTag data, double x, double y, double z) {}

	/** A created entity and where it stands relative to the hologram's origin. */
	record Ghost(Entity entity, double x, double y, double z) {}

	private static int nextId = -1_000_000;

	private GhostEntities() {}

	/** Creates the entities of {@code definitions}, turned and mirrored like the blocks. Entities that cannot be created are left out. */
	static List<Ghost> create(Minecraft mc, List<Definition> definitions, int quarterTurns, boolean mirrored) {
		return create(mc, definitions, quarterTurns, mirrored, true);
	}

	/**
	 * Creates the entities of {@code definitions}, turned and mirrored like the blocks. With {@code frozen} off they are left as the
	 * game would load them (with gravity and physics), which is what is wanted when they are to be saved and made real.
	 */
	static List<Ghost> create(Minecraft mc, List<Definition> definitions, int quarterTurns, boolean mirrored, boolean frozen) {
		List<Ghost> out = new ArrayList<>();
		if (mc.level == null) {
			return out;
		}
		Rotation rotation = switch (quarterTurns & 3) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		for (Definition definition : definitions) {

			try {
				Entity entity = EntityType.loadEntityRecursive(definition.data().copy(), mc.level,
					new EntitySpawnRequest(EntitySpawnReason.LOAD, true), EntityProcessor.NOP);
				if (entity == null) {
					continue;
				}
				float yaw = entity.getYRot();
				if (mirrored) {
					yaw = entity.mirror(Mirror.FRONT_BACK);
				}
				entity.setYRot(yaw);
				yaw = entity.rotate(rotation);
				entity.setYRot(yaw);
				entity.setYHeadRot(yaw);
				if (frozen) {
					// The renderers ask for the entity id; these entities are not in the world, so they get ids no real entity has.
					entity.setId(nextId--);
					entity.setNoGravity(true);
					entity.noPhysics = true;
					entity.tickCount = 0;
				}
				out.add(new Ghost(entity, definition.x(), definition.y(), definition.z()));
			} catch (RuntimeException ignored) {
				// An entity this version cannot build is simply not shown.
			}
		}
		return out;
	}

	/** Submits the frozen entities of one hologram. Returns how many were drawn. */
	static int render(Minecraft mc, Hologram hologram, List<Ghost> ghosts, Vec3 camera, double range, int color, CameraRenderState cameraState,
			PoseStack poseStack, SubmitNodeCollector collector, int budget) {
		EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
		int drawn = 0;
		for (Ghost ghost : ghosts) {
			if (drawn >= budget) {
				break;
			}
			if (!LayerView.shows((int) Math.floor(ghost.y()))) {
				continue;
			}
			double wx = hologram.origin().getX() + ghost.x();
			double wy = hologram.origin().getY() + ghost.y();
			double wz = hologram.origin().getZ() + ghost.z();
			double dx = wx - camera.x, dy = wy - camera.y, dz = wz - camera.z;
			if (dx * dx + dy * dy + dz * dz > range * range) {
				continue;
			}
			Entity entity = ghost.entity();
			entity.setPos(wx, wy, wz);
			entity.setOldPosAndRot();
			try {
				EntityRenderState state = dispatcher.extractEntity(entity, 0.0F);
				// Living entities take the translucent "invisible but shown" path, which the mixin tints like the blocks.
				if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
					living.isInvisible = true;
					living.isInvisibleToPlayer = false;
				}
				ghostColor = color;
				try {
					dispatcher.submit(state, cameraState, dx, dy, dz, poseStack, collector);
				} finally {
					ghostColor = 0;
				}
				drawn++;
			} catch (RuntimeException ignored) {
				// One entity whose renderer fails must not take the rest of the frame with it.
			}
		}
		return drawn;
	}

	static int budget() {
		return MAX_PER_FRAME;
	}
}
