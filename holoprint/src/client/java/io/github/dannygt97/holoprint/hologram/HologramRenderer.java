package io.github.dannygt97.holoprint.hologram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.vertex.QuadInstance;
import io.github.dannygt97.holoprint.config.HoloprintConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the holograms as translucent block models in the world. There is one main hologram (the
 * loaded schematic) and any number of copies pasted next to it.
 *
 * Big schematics are kept light in three ways: each hologram is cut into 16-block cells and the
 * cells that are out of range or out of sight are skipped without looking at their blocks; faces that
 * point away from the camera are not drawn; and there is a ceiling on how many blocks are drawn per
 * frame, nearest first, so a huge build can never bring the frame rate down however much of it is
 * in view.
 */
public final class HologramRenderer {
	static final Direction[] DIRECTIONS = Direction.values();
	/** The block model whose plain white faces stand in for fluids. */
	private static final BlockState FLUID_SHAPE = net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState();
	private static final int WATER_COLOR = 0x3F76E4;
	private static final int LAVA_COLOR = 0xE8620F;

	private static final Map<BlockState, List<BlockStateModelPart>> PARTS = new HashMap<>();
	private static final List<Hologram> ALL = new CopyOnWriteArrayList<>();
	private static volatile Hologram active;
	/** New cells whose quads are built in one frame, and the memory the cached quads may take. */
	private static final int MAX_BUILDS_PER_FRAME = 24;
	/** How many cells whose mesh is only out of date are redone in one frame. */
	private static final int MAX_REFRESHES_PER_FRAME = 2;
	private static final long MAX_CACHE_BYTES = 192L * 1024 * 1024;
	/** A cell with a biome colour is read again from the world this often, so chunks that load later get their colour. */
	private static final long TINT_REFRESH_MS = 5000L;
	private static final java.util.Set<Hologram.Section> CACHED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private static long cachedBytes;
	private static long frame;
	/** Blocks drawn in the last frame and blocks that were in view, for the legend and for tests. */
	public static volatile int lastDrawn;
	public static volatile int lastInView;
	/** How long the last frame spent emitting the hologram's quads, in microseconds, and how many it emitted (for measuring). */
	public static volatile long lastDrawMicros;
	/** How long the last frame spent building meshes on the render thread, in microseconds, and how many it built (for measuring). */
	public static volatile long lastBuildMicros;
	public static volatile int lastBuilds;
	public static volatile int lastQuads;

	private record Candidate(Hologram hologram, Hologram.Section section, double distanceSq) {}
	private record Ready(Hologram hologram, SectionMesh mesh) {}

	private HologramRenderer() {}

	public static void init() {
		RetainedMeshes.init();
		// The quads of every cell hold texture coordinates of the atlas as it was, so a resource reload (F3+T, another pack) makes them be made again.
		net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).registerReloadListener(
			net.minecraft.resources.Identifier.fromNamespaceAndPath("holoprint", "hologram_meshes"),
			(net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> Minecraft.getInstance().execute(HologramRenderer::clearCache));
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			RetainedMeshes.clear();
			HoloprintConfig config = HoloprintConfig.get();
			double range = config.drawDistance;
			int budget = config.blockBudget;
			if (ALL.isEmpty() || !config.showHolograms) {
				lastDrawn = 0;
				lastInView = 0;
				return;
			}
			Vec3 camera = context.levelState().cameraRenderState.pos;
			Frustum frustum = context.levelState().cameraRenderState.cullFrustum;

			List<Candidate> candidates = new ArrayList<>();
			for (Hologram hologram : ALL) {
				// A finished placement is not drawn at all: it is a reference now (what the client cannot see of the world must not keep its outline).
				if (!hologram.visible() || hologram.complete()) {
					continue;
				}
				BlockPos origin = hologram.origin();
				List<Hologram.Section> shown = LayerView.applies(hologram) ? hologram.layerSections(LayerView.layer()) : hologram.sections();
				for (Hologram.Section section : shown) {
					AABB box = new AABB(origin.getX() + section.minX, origin.getY() + section.minY, origin.getZ() + section.minZ,
						origin.getX() + section.maxX, origin.getY() + section.maxY, origin.getZ() + section.maxZ);
					double dx = Math.max(Math.max(box.minX - camera.x, camera.x - box.maxX), 0.0);
					double dy = Math.max(Math.max(box.minY - camera.y, camera.y - box.maxY), 0.0);
					double dz = Math.max(Math.max(box.minZ - camera.z, camera.z - box.maxZ), 0.0);
					double distanceSq = dx * dx + dy * dy + dz * dz;
					if (distanceSq > range * range || (frustum != null && !frustum.isVisible(box))) {
						continue;
					}
					candidates.add(new Candidate(hologram, section, distanceSq));
				}
			}
			candidates.sort(Comparator.comparingDouble(Candidate::distanceSq));

			List<Candidate> drawn = new ArrayList<>();
			int blocks = 0;
			int inView = 0;
			for (Candidate candidate : candidates) {
				int size = candidate.section().entries.size();
				inView += size;
				if (blocks + size <= budget || drawn.isEmpty()) {
					drawn.add(candidate);
					blocks += size;
				}
			}
			lastDrawn = blocks;
			lastInView = inView;
			// The entities of the schematics, frozen in place.
			if (config.showEntities) {
				int left = GhostEntities.budget();
				Minecraft mc = Minecraft.getInstance();
				for (Hologram hologram : ALL) {
					if (left <= 0) {
						break;
					}
					if (hologram.visible() && !hologram.complete() && hologram.showEntities() && hologram.hasEntities()) {
						left -= GhostEntities.render(mc, hologram, hologram.ghosts(mc), camera, range, config.hologramArgb(), context.levelState().cameraRenderState,
							context.poseStack(), context.submitNodeCollector(), left);
					}
				}
			}
			// The chests, signs and other block-entity blocks.
			if (config.showBlockEntities) {
				int left = 400;
				Minecraft mc = Minecraft.getInstance();
				for (Hologram hologram : ALL) {
					if (left <= 0) {
						break;
					}
					if (hologram.visible() && !hologram.complete() && hologram.hasBlockEntities()) {
						left -= GhostBlockEntities.render(mc, hologram, hologram.blockGhosts(mc), camera, range, context.levelState().cameraRenderState,
							context.poseStack(), context.submitNodeCollector(), config.hideBuilt ? hologram.builtCells() : null, config.hologramArgb(), left);
					}
				}
			}
			if (drawn.isEmpty()) {
				return;
			}

			int ghostColor = config.hologramArgb();
			Tints tints = config.biomeColors ? Tints.forWorld() : null;
			long now = System.currentTimeMillis();
			frame++;
			// Each cell's quads are worked out once and kept; only a few new cells are built per frame so a big view never stalls.
			long buildStart = System.nanoTime();
			int builds = 0;
			int refreshes = 0;
			List<Ready> ready = new ArrayList<>(drawn.size());
			for (Candidate candidate : drawn) {
				Hologram.Section section = candidate.section();
				SectionMesh mesh = section.mesh;
				// A cell that has no mesh yet is built at once (up to a few dozen a frame); a mesh that is only out of date (a biome colour read again) is
				// not worth a hitch, so only a couple of those are redone each frame.
				boolean missing = mesh == null;
				if ((missing || isStale(mesh, candidate.hologram(), tints != null, config.fluidAnimations, now)) && (missing ? builds < MAX_BUILDS_PER_FRAME : refreshes < MAX_REFRESHES_PER_FRAME)) {
					if (!missing) {
						refreshes++;
					}
					mesh = buildMesh(candidate.hologram(), section, tints, config.fluidAnimations, now);
					store(section, mesh);
					builds++;
				}
				if (mesh != null) {
					mesh.lastUsed = frame;
					ready.add(new Ready(candidate.hologram(), mesh));
				}
			}
			lastBuildMicros = (System.nanoTime() - buildStart) / 1000;
			lastBuilds = builds;
			trimCache();
			int quadCount = 0;
			for (Ready item : ready) {
				quadCount += item.mesh().quads;
			}
			lastQuads = quadCount;
			// The quads stay on the card between frames (RetainedMeshes draws them); only the flow arrows are still copied every frame.
			boolean retained = config.retainedMeshes;
			RetainedMeshes.begin(camera, ghostColor, config.hideBuilt, config.blockOutlines ? 0xFF000000 | config.outlineColor : 0);
			if (retained) {
			for (Ready item : ready) {
			RetainedMeshes.add(item.hologram(), item.mesh());
			}
			}
			RenderType type = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), type, (pose, consumer) -> {
			if (!retained) {
			long started = System.nanoTime();
			drawQuads(consumer, ready, camera, config, ghostColor);
			lastDrawMicros = (System.nanoTime() - started) / 1000;
			}
				int arrowColor = Math.max(0xD8, (ghostColor >>> 24) & 0xFF) << 24 | config.flowColor;
				for (Ready item : config.showFlowArrows ? ready : List.<Ready>of()) {
					Hologram hologram = item.hologram();
					SectionMesh mesh = item.mesh();
					BlockPos origin = hologram.origin();
					float ox = (float) (origin.getX() - camera.x), oy = (float) (origin.getY() - camera.y), oz = (float) (origin.getZ() - camera.z);
					java.util.BitSet built = config.hideBuilt ? hologram.builtCells() : null;
					for (Hologram.Entry entry : mesh.arrows) {
						BlockPos local = entry.local();
						if (!LayerView.shows(hologram, local.getY())) {
							continue;
						}
						if (built != null && built.get((local.getY() * hologram.sizeZ() + local.getZ()) * hologram.sizeX() + local.getX())) {
							continue;
						}
						flowArrows(consumer, entry, ox + local.getX(), oy + local.getY(), oz + local.getZ(), arrowColor);
					}
				}
			});
		});
	}

	/** Emits the quads of the ready cells into {@code consumer}. */
	private static void drawQuads(com.mojang.blaze3d.vertex.VertexConsumer consumer, List<Ready> ready, Vec3 camera, HoloprintConfig config, int ghostColor) {
		for (Ready item : ready) {
			Hologram hologram = item.hologram();
			SectionMesh mesh = item.mesh();
			BlockPos origin = hologram.origin();
			float ox = (float) (origin.getX() - camera.x), oy = (float) (origin.getY() - camera.y), oz = (float) (origin.getZ() - camera.z);
			// Blocks that are already built in the world are left out, so only what is still to do is drawn.
			java.util.BitSet built = config.hideBuilt ? hologram.builtCells() : null;
			for (int q = 0; q < mesh.quads; q++) {
				if (!LayerView.shows(hologram, mesh.block[3 * q + 1])) {
					continue;
				}
				if (built != null && built.get(mesh.cell[q])) {
					continue;
				}
				int cull = mesh.cull[q];
				if (cull >= 0 && !facesCamera(DIRECTIONS[cull], ox + mesh.block[3 * q], oy + mesh.block[3 * q + 1], oz + mesh.block[3 * q + 2])) {
					continue;
				}
				int color = mesh.absolute[q] ? (ghostColor & 0xFF000000) | mesh.rgb[q] : multiply(ghostColor, mesh.rgb[q]);
				Direction facing = DIRECTIONS[mesh.normal[q]];
				float nx = facing.getStepX(), ny = facing.getStepY(), nz = facing.getStepZ();
				int light = mesh.light[q];
				float push = mesh.absolute[q] ? 0.0F : RetainedMeshes.FACE_PUSH;
				for (int v = 0; v < 4; v++) {
					int at = (q * 4 + v) * 5;
					consumer.addVertex(ox + mesh.vertices[at] + nx * push, oy + mesh.vertices[at + 1] + ny * push, oz + mesh.vertices[at + 2] + nz * push, color,
						mesh.vertices[at + 3], mesh.vertices[at + 4], OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
				}
			}
		}
	}

	/** A face is worth drawing only when it points towards the camera; dx, dy, dz is the block's corner minus the camera. */
	private static boolean facesCamera(Direction direction, float dx, float dy, float dz) {
		return switch (direction) {
			case UP -> dy + 1.0F < 0.0F;
			case DOWN -> dy > 0.0F;
			case EAST -> dx + 1.0F < 0.0F;
			case WEST -> dx > 0.0F;
			case SOUTH -> dz + 1.0F < 0.0F;
			case NORTH -> dz > 0.0F;
		};
	}

	// --- flow arrows on fluids ---

	private static float[] solidUv;

	/** A point inside the plain white block's texture, so flat shapes take the colour they are given. */
	private static float[] solidUv() {
		if (solidUv == null) {
			BakedQuad quad = partsOf(FLUID_SHAPE).get(0).getQuads(Direction.UP).get(0);
			float u = 0.0F, v = 0.0F;
			for (int i = 0; i < 4; i++) {
				u += net.minecraft.client.model.geom.builders.UVPair.unpackU(quad.packedUV(i)) / 4.0F;
				v += net.minecraft.client.model.geom.builders.UVPair.unpackV(quad.packedUV(i)) / 4.0F;
			}
			solidUv = new float[] {u, v};
		}
		return solidUv;
	}

	/** Draws the arrows of one fluid block: along the flow on its top face, or pointing down on its sides when it falls. */
	private static void flowArrows(com.mojang.blaze3d.vertex.VertexConsumer consumer, Hologram.Entry entry, float dx, float dy, float dz, int color) {
		if (entry.flow() == Hologram.FALLING) {
			for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
				if (entry.faceVisible(side) && facesCamera(side, dx, dy, dz)) {
					float nx = side.getStepX(), nz = side.getStepZ();
					// perpendicular along the face, and the centre pushed a hair outwards
					arrow(consumer, color, dx + 0.5F + nx * 0.51F, dy + 0.5F, dz + 0.5F + nz * 0.51F, 0, -1, 0, -nz, 0, nx, nx, 0, nz);
				}
			}
		} else if (entry.faceVisible(Direction.UP) && facesCamera(Direction.UP, dx, dy, dz)) {
			double angle = (entry.flow() - Hologram.FLOW_FIRST) * Math.PI / 4.0;
			float ax = (float) Math.cos(angle), az = (float) Math.sin(angle);
			float[] h = entry.heights();
			arrow(consumer, color, dx + 0.5F, dy + (h[0] + h[1] + h[2] + h[3]) / 4.0F + 0.01F, dz + 0.5F, ax, 0, az, -az, 0, ax, 0, 1, 0);
		}
	}

	/**
	 * One flat arrow centred on (cx, cy, cz): its direction is d, its width runs along p and n is the way the
	 * face looks. Drawn both ways round so it shows whichever side the camera is on.
	 */
	private static void arrow(com.mojang.blaze3d.vertex.VertexConsumer consumer, int color, float cx, float cy, float cz,
			float dx, float dy, float dz, float px, float py, float pz, float nx, float ny, float nz) {
		float[] uv = solidUv();
		// shaft, then head: (along, across) pairs
		float[][] shaft = {{-0.30F, -0.07F}, {0.06F, -0.07F}, {0.06F, 0.07F}, {-0.30F, 0.07F}};
		float[][] head = {{0.06F, -0.22F}, {0.40F, 0.0F}, {0.40F, 0.0F}, {0.06F, 0.22F}};
		for (float[][] shape : new float[][][] {shaft, head}) {
			for (int pass = 0; pass < 1; pass++) {
				for (int i = 0; i < 4; i++) {
					float[] corner = shape[pass == 0 ? 3 - i : i];
					consumer.addVertex(cx + dx * corner[0] + px * corner[1], cy + dy * corner[0] + py * corner[1], cz + dz * corner[0] + pz * corner[1])
						.setColor(color).setUv(uv[0], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
						.setNormal(pass == 0 ? nx : -nx, pass == 0 ? ny : -ny, pass == 0 ? nz : -nz);
				}
			}
		}
	}

	/**
	 * The game's own tint sources (grass, leaves, water, redstone, stems...) read in the real world, so
	 * a hologram of grass or leaves takes the colour of the biome it stands in.
	 */
	private static final class Tints {
		private final net.minecraft.client.color.block.BlockColors colors;
		private final net.minecraft.client.multiplayer.ClientLevel level;

		private Tints(net.minecraft.client.color.block.BlockColors colors, net.minecraft.client.multiplayer.ClientLevel level) {
			this.colors = colors;
			this.level = level;
		}

		static Tints forWorld() {
			Minecraft mc = Minecraft.getInstance();
			return mc.level == null ? null : new Tints(mc.getBlockColors(), mc.level);
		}

		/** The colour of water in the biome at {@code at}, as an opaque RGB value. */
		int fluid(net.minecraft.client.color.block.BlockTintSource source, BlockState state, BlockPos at) {
			return 0xFF000000 | source.colorInWorld(state, level, at);
		}

		/** The tint of one quad as an opaque RGB value, or -1 when it has none. */
		int of(BlockState state, BlockPos at, int index) {
			net.minecraft.client.color.block.BlockTintSource source = colors.getTintSource(state, index);
			if (source == null) {
				return -1;
			}
			return 0xFF000000 | source.colorInWorld(state, level, at);
		}
	}

	private static boolean isStale(SectionMesh mesh, Hologram hologram, boolean biomeColors, boolean fluidTextures, long now) {
		if (mesh.builtWithBiomeColors != biomeColors || mesh.fluidTextured != fluidTextures) {
			return true;
		}
		// While it is carried the colours keep the last place they were read at (reading them again at every step costs frames); they catch up once it is put down.
		boolean moved = !hologram.moving() && !hologram.origin().equals(mesh.builtOrigin);
		return mesh.tinted && (moved || now - mesh.builtAt > TINT_REFRESH_MS);
	}

	/** Works out every visible quad of one cell, with the tint it takes from the world at the hologram's current place. */
	private static SectionMesh buildMesh(Hologram hologram, Hologram.Section section, Tints tints, boolean fluidTextures, long now) {
		MeshData data = new MeshData();
		List<Hologram.Entry> arrows = new ArrayList<>();
		BlockPos origin = hologram.origin();
		int sizeX = hologram.sizeX(), sizeZ = hologram.sizeZ();
		QuadInstance instance = new QuadInstance();
		instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
		boolean tinted = false;
		for (Hologram.Entry entry : section.entries) {
			BlockPos local = entry.local();
			BlockPos at = origin.offset(local);
			boolean fluid = entry.fluid() != Hologram.NO_FLUID;
			int index = (local.getY() * sizeZ + local.getZ()) * sizeX + local.getX();
			if (fluid) {
				// Water and lava are shaped by their level, like the game does, and textured with the game's own (moving) sprites or
				// drawn as flat colour when the animations are off.
				tinted |= fluidQuads(data, entry, index, tints, at, fluidTextures);
				if (entry.flow() != Hologram.NO_FLOW) {
					arrows.add(entry);
				}
				if (!entry.waterlogged()) {
					continue;
				}
			}
			for (BlockStateModelPart part : partsOf(entry.state())) {
				for (int side = -1; side < 6; side++) {
					if (side >= 0 && !entry.modelFaceVisible(DIRECTIONS[side])) {
						continue;
					}
					for (BakedQuad quad : part.getQuads(side < 0 ? null : DIRECTIONS[side])) {
						int color = 0xFFFFFF;
						if (tints != null && quad.materialInfo().isTinted()) {
							int tint = tints.of(entry.state(), at, quad.materialInfo().tintIndex());
							if (tint != -1) {
								color = tint & 0xFFFFFF;
								tinted = true;
							}
						}
						data.begin(side, quad.direction(), color, false, instance.getLightCoordsWithEmission(0, quad.materialInfo().lightEmission()), index, local);
						for (int v = 0; v < 4; v++) {
							org.joml.Vector3fc position = quad.position(v);
							data.vertex(local.getX() + position.x(), local.getY() + position.y(), local.getZ() + position.z(),
								net.minecraft.client.model.geom.builders.UVPair.unpackU(quad.packedUV(v)), net.minecraft.client.model.geom.builders.UVPair.unpackV(quad.packedUV(v)));
						}
					}
				}
			}
		}
		return new SectionMesh(data.vertices.toFloatArray(), data.cull.size(), data.block.toIntArray(), data.cell.toIntArray(), data.cull.toByteArray(), data.normal.toByteArray(),
			data.rgb.toIntArray(), data.absolute.toBooleanArray(), data.light.toIntArray(), arrows, tinted, tints != null, fluidTextures, origin, now, hologram.stampKey(section.minX, section.minY, section.minZ));
	}

	/** The quads of one cell as they are collected. */
	private static final class MeshData {
		final it.unimi.dsi.fastutil.floats.FloatArrayList vertices = new it.unimi.dsi.fastutil.floats.FloatArrayList();
		final it.unimi.dsi.fastutil.ints.IntArrayList block = new it.unimi.dsi.fastutil.ints.IntArrayList();
		final it.unimi.dsi.fastutil.ints.IntArrayList cell = new it.unimi.dsi.fastutil.ints.IntArrayList();
		final it.unimi.dsi.fastutil.ints.IntArrayList rgb = new it.unimi.dsi.fastutil.ints.IntArrayList();
		final it.unimi.dsi.fastutil.ints.IntArrayList light = new it.unimi.dsi.fastutil.ints.IntArrayList();
		final it.unimi.dsi.fastutil.bytes.ByteArrayList cull = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
		final it.unimi.dsi.fastutil.bytes.ByteArrayList normal = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
		final it.unimi.dsi.fastutil.booleans.BooleanArrayList absolute = new it.unimi.dsi.fastutil.booleans.BooleanArrayList();

		/** Starts a quad; its four vertices follow. */
		void begin(int side, Direction facing, int color, boolean replacesColor, int lightCoords, int index, BlockPos local) {
			cull.add((byte) side);
			normal.add((byte) facing.get3DDataValue());
			rgb.add(color);
			absolute.add(replacesColor);
			light.add(lightCoords);
			cell.add(index);
			block.add(local.getX());
			block.add(local.getY());
			block.add(local.getZ());
		}

		void vertex(float x, float y, float z, float u, float v) {
			vertices.add(x);
			vertices.add(y);
			vertices.add(z);
			vertices.add(u);
			vertices.add(v);
		}
	}

	/**
	 * The exposed faces of a water or lava block, as tall as its level says (a source reaches 8/9 of a block, flowing fluid less, fluid
	 * under more fluid the whole block), with a sloping top. Returns true when a colour was read from the world.
	 */
	private static boolean fluidQuads(MeshData data, Hologram.Entry entry, int index, Tints tints, BlockPos at, boolean textures) {
		BlockPos local = entry.local();
		float x = local.getX(), y = local.getY(), z = local.getZ();
		float[] h = entry.heights();
		boolean lava = entry.fluid() == Hologram.LAVA;
		float u0, u1, v0, v1, fu0, fu1, fv0, fv1;
		int rgb;
		boolean fromWorld = false;
		if (textures) {
			net.minecraft.client.renderer.block.FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(entry.state().getFluidState());
			net.minecraft.client.renderer.texture.TextureAtlasSprite still = model.stillMaterial().sprite();
			net.minecraft.client.renderer.texture.TextureAtlasSprite flowing = model.flowingMaterial().sprite();
			u0 = still.getU0();
			u1 = still.getU1();
			v0 = still.getV0();
			v1 = still.getV1();
			fu0 = flowing.getU0();
			fu1 = flowing.getU1();
			fv0 = flowing.getV0();
			fv1 = flowing.getV1();
			net.minecraft.client.color.block.BlockTintSource source = model.tintSource();
			if (source == null) {
				rgb = 0xFFFFFF;
			} else if (tints != null) {
				rgb = tints.fluid(source, entry.state(), at) & 0xFFFFFF;
				fromWorld = true;
			} else {
				rgb = source.color(entry.state()) & 0xFFFFFF;
			}
		} else {
			float[] uv = solidUv();
			u0 = u1 = fu0 = fu1 = uv[0];
			v0 = v1 = fv0 = fv1 = uv[1];
			rgb = lava ? LAVA_COLOR : WATER_COLOR;
		}
		int light = LightCoordsUtil.FULL_BRIGHT;
		if (entry.faceVisible(Direction.UP)) {
			data.begin(Direction.UP.get3DDataValue(), Direction.UP, rgb, true, light, index, local);
			boolean moving = textures && entry.flow() >= Hologram.FLOW_FIRST;
			double angle = (entry.flow() - Hologram.FLOW_FIRST) * Math.PI / 4.0;
			float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
			// corners in the order of the top face of a block: (x0, z0), (x0, z1), (x1, z1), (x1, z0)
			int[][] corners = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
			for (int[] corner : corners) {
				float cu, cv;
				if (moving) {
					// the flowing texture is turned so that it runs the way the fluid does
					float px = corner[0] - 0.5F, pz = corner[1] - 0.5F;
					float across = px * sin - pz * cos, along = px * cos + pz * sin;
					cu = (fu0 + fu1) / 2.0F + across * 0.5F * (fu1 - fu0);
					cv = (fv0 + fv1) / 2.0F + along * 0.5F * (fv1 - fv0);
				} else {
					cu = u0 + corner[0] * (u1 - u0);
					cv = v0 + corner[1] * (v1 - v0);
				}
				data.vertex(x + corner[0], y + h[corner[1] * 2 + corner[0]], z + corner[1], cu, cv);
			}
		}
		if (entry.faceVisible(Direction.DOWN)) {
			data.begin(Direction.DOWN.get3DDataValue(), Direction.DOWN, rgb, true, light, index, local);
			data.vertex(x, y, z + 1, u0, v1);
			data.vertex(x, y, z, u0, v0);
			data.vertex(x + 1, y, z, u1, v0);
			data.vertex(x + 1, y, z + 1, u1, v1);
		}
		// Sides: the flowing texture, half its width across a block and as far down as the surface is high.
		for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			if (!entry.faceVisible(side)) {
				continue;
			}
			float xl, zl, xr, zr, hl, hr;
			switch (side) {
				case NORTH -> {
					xl = x + 1; zl = z; xr = x; zr = z; hl = h[1]; hr = h[0];
				}
				case SOUTH -> {
					xl = x; zl = z + 1; xr = x + 1; zr = z + 1; hl = h[2]; hr = h[3];
				}
				case EAST -> {
					xl = x + 1; zl = z + 1; xr = x + 1; zr = z; hl = h[3]; hr = h[1];
				}
				default -> {
					xl = x; zl = z; xr = x; zr = z + 1; hl = h[0]; hr = h[2];
				}
			}
			float uLeft = fu0, uRight = fu0 + (fu1 - fu0) * 0.5F;
			float vBottom = fv0 + (fv1 - fv0) * 0.5F;
			float vTopLeft = fv0 + (fv1 - fv0) * (1.0F - hl) * 0.5F, vTopRight = fv0 + (fv1 - fv0) * (1.0F - hr) * 0.5F;
			data.begin(side.get3DDataValue(), side, rgb, true, light, index, local);
			data.vertex(xl, y + hl, zl, uLeft, vTopLeft);
			data.vertex(xl, y, zl, uLeft, vBottom);
			data.vertex(xr, y, zr, uRight, vBottom);
			data.vertex(xr, y + hr, zr, uRight, vTopRight);
		}
		return fromWorld;
	}

	private static void store(Hologram.Section section, SectionMesh mesh) {
		SectionMesh old = section.mesh;
		if (old != null) {
		cachedBytes -= old.weight();
		RetainedMeshes.release(old);
		}
		section.mesh = mesh;
		cachedBytes += mesh.weight();
		CACHED.add(section);
	}

	/** Frees the cells that have not been drawn for a while, and the least recently used ones when the cache grows too big. */
	private static void trimCache() {
		if (frame % 120 != 0 && cachedBytes <= MAX_CACHE_BYTES) {
			return;
		}
		long idle = 3600;
		List<Hologram.Section> order = new ArrayList<>(CACHED);
		order.sort(Comparator.comparingLong(section -> section.mesh == null ? Long.MIN_VALUE : section.mesh.lastUsed));
		long keep = cachedBytes > MAX_CACHE_BYTES ? MAX_CACHE_BYTES * 7 / 10 : Long.MAX_VALUE;
		for (Hologram.Section section : order) {
			SectionMesh mesh = section.mesh;
			if (mesh == null || frame - mesh.lastUsed > idle || cachedBytes > keep) {
				drop(section);
			} else if (keep == Long.MAX_VALUE) {
				break;
			}
		}
	}

	private static void drop(Hologram.Section section) {
		SectionMesh mesh = section.mesh;
		if (mesh != null) {
		cachedBytes -= mesh.weight();
		RetainedMeshes.release(mesh);
		}
		section.mesh = null;
		CACHED.remove(section);
	}

	private static void forgetMeshes() {
		for (Hologram.Section section : new ArrayList<>(CACHED)) {
			drop(section);
		}
		cachedBytes = 0;
	}

	/** Frees the cached quads of some cells that are no longer used. */
	static void forgetSections(List<Hologram.Section> sections) {
		for (Hologram.Section section : sections) {
			drop(section);
		}
	}

	/** Frees the cached quads of a hologram that is gone. */
	private static void forget(Hologram hologram) {
		if (hologram != null) {
			for (Hologram.Section section : hologram.sections()) {
				drop(section);
			}
		}
	}

	private static int multiply(int argb, int rgb) {
		int r = ((argb >> 16) & 0xFF) * ((rgb >> 16) & 0xFF) / 255;
		int g = ((argb >> 8) & 0xFF) * ((rgb >> 8) & 0xFF) / 255;
		int b = (argb & 0xFF) * (rgb & 0xFF) / 255;
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	private static List<BlockStateModelPart> partsOf(BlockState state) {
		return PARTS.computeIfAbsent(state, s -> {
			BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(s);
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(42L), parts);
			return parts;
		});
	}

	/** True when the last frame had to leave out the farthest blocks of a very big hologram. */
	public static boolean isTrimmed() {
		return lastInView > lastDrawn;
	}

	/** Makes the given hologram the main one, replacing the previous main hologram (copies stay). */
	public static void show(Hologram hologram) {
		Hologram previous = active;
		if (previous != null) {
			ALL.remove(previous);
			forget(previous);
		}
		active = hologram;
		if (hologram != null && !ALL.contains(hologram)) {
			ALL.add(0, hologram);
		}
	}

	/** Adds a new placement and makes it the one the Move mode works on; the others stay. */
	public static void addAndActivate(Hologram hologram) {
		if (!ALL.contains(hologram)) {
			ALL.add(hologram);
		}
		active = hologram;
	}

	/** A finished placement the user picked in the list to see where it is: it gets the gold box and the big name, though it is not the one Move works on. */
	private static volatile Hologram highlighted;

	public static Hologram highlighted() {
		return highlighted;
	}

	/** The one placement that is selected: a finished one picked in the list, or else the one the editor works on. */
	public static Hologram selected() {
		Hologram picked = highlighted;
		return picked != null && ALL.contains(picked) && picked.complete() ? picked : active;
	}

	/** Highlights {@code hologram}, or stops highlighting it when it already is. */
	public static void toggleHighlight(Hologram hologram) {
		highlighted = highlighted == hologram ? null : hologram;
	}

	/** Makes an existing placement the one the Move mode works on. */
	public static void makeActive(Hologram hologram) {
		if (ALL.contains(hologram) && !hologram.complete()) {
			highlighted = null;
			active = hologram;
		}
	}

	/** The given placement stops being the one Move works on (it is finished); nothing else changes. */
	public static void deselect(Hologram hologram) {
		if (active == hologram) {
			active = null;
		}
	}

	/** Removes every hologram, the main one and the copies. */
	public static void hide() {
		active = null;
		forgetMeshes();
		ALL.clear();
	}

	public static Hologram active() {
		return active;
	}

	/** Shows an extra hologram next to the main one (a pasted copy, or one being carried). */
	public static void add(Hologram hologram) {
		if (!ALL.contains(hologram)) {
			ALL.add(hologram);
		}
	}

	public static void remove(Hologram hologram) {
		if (highlighted == hologram) {
			highlighted = null;
		}
		ALL.remove(hologram);
		forget(hologram);
		if (active == hologram) {
			active = ALL.isEmpty() ? null : ALL.get(ALL.size() - 1);
		}
	}

	public static List<Hologram> all() {
		return ALL;
	}

	/**
	 * The visible hologram a ray passes through first within the given distance, or null. The ray is
	 * sampled in small steps, which is plenty for picking whole constructions.
	 */
	public static Hologram pick(Vec3 from, Vec3 direction, double maxDistance, Hologram ignore) {
		for (double t = 0.0; t <= maxDistance; t += 0.2) {
			BlockPos at = BlockPos.containing(from.add(direction.scale(t)));
			for (Hologram hologram : ALL) {
				if (hologram != ignore && hologram.visible() && !hologram.complete() && hologram.containsWorld(at)) {
					return hologram;
				}
			}
		}
		return null;
	}

	/** A finished placement the ray passes through (they cannot be picked, but the user is told why), or null. */
	public static Hologram pickComplete(Vec3 from, Vec3 direction, double maxDistance) {
		for (double t = 0.0; t <= maxDistance; t += 0.2) {
			BlockPos at = BlockPos.containing(from.add(direction.scale(t)));
			for (Hologram hologram : ALL) {
				if (hologram.visible() && hologram.complete() && hologram.containsWorld(at)) {
					return hologram;
				}
			}
		}
		return null;
	}

	/** How many times the cached models and meshes were forgotten (for the tests). */
	public static volatile int cacheClears;

	/** Forgets the cached models and meshes: after a resource reload the blocks sit elsewhere in the texture atlas and look different. */
	public static void clearCache() {
		PARTS.clear();
		solidUv = null;
		forgetMeshes();
		cacheClears++;
	}
}
