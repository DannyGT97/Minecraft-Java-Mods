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
	/** While the vertices of a hologram painting are written (later in the frame): the ARGB it is drawn with; 0 otherwise. */
	public static volatile int paintingColor;
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
				Entity entity = EntityType.loadEntityRecursive(placed(definition), mc.level,
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

	/**
	 * The saved data of {@code definition} with its position (and the block a hanging entity hangs on) where the hologram puts it. The data keeps
	 * the coordinates of the world it was copied from, and the game refuses a frame or painting whose block is far from its position.
	 */
	private static net.minecraft.nbt.CompoundTag placed(Definition definition) {
		net.minecraft.nbt.CompoundTag data = definition.data().copy();
		net.minecraft.nbt.ListTag pos = new net.minecraft.nbt.ListTag();
		pos.add(net.minecraft.nbt.DoubleTag.valueOf(definition.x()));
		pos.add(net.minecraft.nbt.DoubleTag.valueOf(definition.y()));
		pos.add(net.minecraft.nbt.DoubleTag.valueOf(definition.z()));
		data.put("Pos", pos);
		int bx = (int) Math.floor(definition.x()), by = (int) Math.floor(definition.y()), bz = (int) Math.floor(definition.z());
		if (data.contains("block_pos")) {
			data.putIntArray("block_pos", new int[] {bx, by, bz});
		}
		if (data.contains("TileX")) {
			data.putInt("TileX", bx);
			data.putInt("TileY", by);
			data.putInt("TileZ", bz);
		}
		return data;
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
			if (!LayerView.shows(hologram, (int) Math.floor(ghost.y()))) {
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
					if (living instanceof net.minecraft.client.renderer.entity.state.ArmedEntityRenderState armed) {
						fade(armed.rightHandItemState, color);
						fade(armed.leftHandItemState, color);
					}
				} else if (state instanceof net.minecraft.client.renderer.entity.state.ItemClusterRenderState dropped) {
					fade(dropped.item, color);
				} else if (state instanceof net.minecraft.client.renderer.entity.state.ItemFrameRenderState frame) {
					fade(frame.item, color);
				}
				ghostColor = color;
				try {
					// Vehicles and the like (minecarts, boats) hand their models to the collector with a ready texture: those come out translucent like block entities.
					dispatcher.submit(state, cameraState, dx, dy, dz, poseStack, state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState ? collector : TranslucentCollector.wrap(collector, color));
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

	/**
	 * Makes an item that is about to be drawn translucent like the rest of the hologram: its quads move to the translucent item
	 * sheet and the item's tint takes the hologram's opacity (and, where the item has no tint of its own, the hologram's colour).
	 * The item state is rebuilt by every extraction, so nothing has to be put back.
	 */
	private static void fade(net.minecraft.client.renderer.item.ItemStackRenderState item, int argb) {
		if (item == null || item.isEmpty()) {
			return;
		}
		var accessor = (io.github.dannygt97.holoprint.mixin.ItemStackRenderStateAccessor) item;
		var layers = accessor.holoprint$layers();
		int count = accessor.holoprint$layerCount();
		int alpha = argb & 0xFF000000;
		for (int i = 0; i < count && i < layers.length; i++) {
			var layer = layers[i];
			var quads = ((io.github.dannygt97.holoprint.mixin.LayerRenderStateAccessor) layer).holoprint$quads();
			if (quads == null || quads.isEmpty()) {
				continue;
			}
			var tints = layer.tintLayers();
			// the tint every quad of the item that has none of its own is given: one more than the highest the item uses
			int own = 0;
			for (var quad : quads.all()) {
				own = Math.max(own, quad.materialInfo().tintIndex() + 1);
			}
			while (tints.size() < own) {
				tints.add(-1);
			}
			for (int t = 0; t < own; t++) {
				tints.set(t, alpha | (tints.getInt(t) & 0xFFFFFF));
			}
			tints.add(argb);
			var faded = new ArrayList<net.minecraft.client.resources.model.geometry.BakedQuad>(quads.all().size());
			for (var quad : quads.all()) {
				var info = quad.materialInfo();
				boolean blocks = info.sprite().atlasLocation().equals(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
				var material = new net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo(info.sprite(),
					net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT,
					blocks ? net.minecraft.client.renderer.Sheets.translucentBlockItemSheet() : net.minecraft.client.renderer.Sheets.translucentItemSheet(),
					blocks ? net.minecraft.client.renderer.Sheets.translucentBlockItemGlintSheet() : net.minecraft.client.renderer.Sheets.translucentItemGlintSheet(),
					blocks ? net.minecraft.client.renderer.Sheets.translucentBlockItemGlintSpecialSheet() : net.minecraft.client.renderer.Sheets.translucentItemGlintSpecialSheet(),
					info.isTinted() ? info.tintIndex() : own, info.shadeDirectionOverride(), info.lightEmission());
				faded.add(new net.minecraft.client.resources.model.geometry.BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
					quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(), material));
			}
			layer.setQuads(net.minecraft.client.resources.model.geometry.ItemQuads.split(faded));
		}
	}

	static int budget() {
		return MAX_PER_FRAME;
	}
}
