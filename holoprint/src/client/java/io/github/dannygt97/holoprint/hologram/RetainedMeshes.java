package io.github.dannygt97.holoprint.hologram;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The quads of every cell that is in view, kept in the graphics card between frames. A cell's mesh is worked out once (see {@link SectionMesh}) and
 * turned into vertex buffers; drawing a frame is then one draw call per cell, with nothing copied: the hologram's colour and opacity go in as the
 * colour the game multiplies every vertex by, and the hologram's place as a shift of the view. What the frame-by-frame way did (skipping the faces
 * turned away from the camera, the blocks already built, the layers not shown) is done when the buffers are made, or by the card (the faces turned away).
 */
final class RetainedMeshes {
	/** What one cell keeps on the card: normal quads (their colour is multiplied by the hologram's) and fluid quads (their colour replaces it). */
	static final class Gpu {
		GpuBuffer normal;
		int normalQuads;
		GpuBuffer fluid;
		int fluidQuads;
		/** What the buffers were made for: the cells hidden because they are built, and the layer shown. */
		int builtStamp;
		boolean hidingBuilt;
		int layerStamp;
		/** The outline of the blocks: its lines (the game draws each as a quad: four vertices, six indices) and the colour it was made in (0 when there is none). */
		GpuBuffer lines;
		int lineIndexCount;
		int outlineArgb;

		void release() {
			for (GpuBuffer buffer : new GpuBuffer[] {normal, fluid, lines}) {
				if (buffer != null) {
					CLOSE.add(buffer);
				}
			}
			normal = null;
			fluid = null;
			lines = null;
		}
	}

	/** Buffers to free on the render thread (cells are dropped from other threads too). */
	private static final ConcurrentLinkedQueue<GpuBuffer> CLOSE = new ConcurrentLinkedQueue<>();
	private record Item(Hologram hologram, SectionMesh mesh) {}
	private static final List<Item> QUEUED = new ArrayList<>();
	private static int ghostArgb;
	private static Vec3 camera = Vec3.ZERO;
	private static boolean hideBuilt;
	/** The outline's colour as ARGB, or 0 for no outline. */
	private static int outlineArgb;
	private static java.lang.reflect.Field fogField;
	/** What the last frame drew (for measuring): cells queued, cells drawn, quads drawn, buffers made. */
	public static volatile int lastQueued, lastCells, lastQuadsDrawn, lastMade;
	private static GpuBuffer indexBuffer;
	private static int indexQuads;
	private static GpuBuffer lineIndexBuffer;
	private static int lineIndexLines;
	private static final float OUTLINE_WIDTH = 1.5F;
	private static final int BIAS = 1 << 19;

	private RetainedMeshes() {}

	static void init() {
		LevelRenderEvents.END_MAIN.register(RetainedMeshes::draw);
	}

	/** What is to be drawn this frame, set while the frame's submissions are collected and drawn right after the translucent features. */
	static void begin(Vec3 cameraPosition, int ghost, boolean hideBuiltCells, int outline) {
		QUEUED.clear();
		outlineArgb = outline;
		camera = cameraPosition;
		ghostArgb = ghost;
		hideBuilt = hideBuiltCells;
	}

	static void add(Hologram hologram, SectionMesh mesh) {
		QUEUED.add(new Item(hologram, mesh));
	}

	/** Nothing is to be drawn (the frame had no hologram to draw). */
	static void clear() {
		QUEUED.clear();
	}

	/** Frees what a dropped cell held. */
	static void release(SectionMesh mesh) {
		Gpu gpu = mesh.gpu;
		if (gpu != null) {
			gpu.release();
			mesh.gpu = null;
		}
	}

	private static void draw(LevelRenderContext context) {
		for (GpuBuffer buffer; (buffer = CLOSE.poll()) != null;) {
			buffer.close();
		}
		if (QUEUED.isEmpty()) {
			return;
		}
		long started = System.nanoTime();
		Minecraft mc = Minecraft.getInstance();
		RenderTarget target = mc.gameRenderer.mainRenderTarget();
		var device = RenderSystem.getDevice();
		int maxQuads = 0;
		int maxLineIndices = 0;
		int queuedCount = QUEUED.size();
		int made = 0;
		List<Item> items = new ArrayList<>(QUEUED.size());
		for (Item item : QUEUED) {
			boolean had = item.mesh().gpu != null;
			Gpu gpu = ensure(item);
			if (!had || item.mesh().gpu != gpu) {
				made++;
			}
			if (gpu != null && (gpu.normal != null || gpu.fluid != null)) {
				items.add(item);
				maxQuads = Math.max(maxQuads, Math.max(gpu.normalQuads, gpu.fluidQuads));
				maxLineIndices = Math.max(maxLineIndices, gpu.lineIndexCount);
			}
		}
		QUEUED.clear();
		if (items.isEmpty()) {
			return;
		}
		Matrix4f view = new Matrix4f(context.levelState().cameraRenderState.viewRotationMatrix);
		PreparedRenderType base = RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS).prepare();
		GpuBuffer indices = indexBuffer(maxQuads);
		Vector4f normalColour = new Vector4f(((ghostArgb >> 16) & 0xFF) / 255.0F, ((ghostArgb >> 8) & 0xFF) / 255.0F, (ghostArgb & 0xFF) / 255.0F, ((ghostArgb >>> 24) & 0xFF) / 255.0F);
		Vector4f fluidColour = new Vector4f(1.0F, 1.0F, 1.0F, normalColour.w);
		var currentFog = RenderSystem.getShaderFog();
		// The hologram is drawn without fog, as it always was: the features of the world are drawn that way, and far away it would fade into the sky.
		var noFog = noFog(mc);
		if (noFog != null) {
			RenderSystem.setShaderFog(noFog);
		}
		try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "holoprint meshes", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
			// Far to near, so the translucent faces blend over one another (the nearer ones write depth and would hide the farther ones if drawn first).
			java.util.Map<Hologram, Matrix4f> models = new java.util.IdentityHashMap<>();
			java.util.Map<Hologram, PreparedRenderType[]> types = new java.util.IdentityHashMap<>();
			for (int i = items.size() - 1; i >= 0; i--) {
				Item item = items.get(i);
				PreparedRenderType[] pair = types.get(item.hologram());
				if (pair == null) {
					BlockPos o = item.hologram().origin();
					Matrix4f model = new Matrix4f(view).translate((float) (o.getX() - camera.x), (float) (o.getY() - camera.y), (float) (o.getZ() - camera.z));
					models.put(item.hologram(), model);
					pair = new PreparedRenderType[] {
						new PreparedRenderType(base.name(), base.pipeline(), base.oitPipelineSet(),
							RenderSystem.getDynamicUniforms().writeTransform(model, normalColour, new Vector3f(), new Matrix4f()), base.scissorState(), base.textures()),
						new PreparedRenderType(base.name(), base.pipeline(), base.oitPipelineSet(),
							RenderSystem.getDynamicUniforms().writeTransform(model, fluidColour, new Vector3f(), new Matrix4f()), base.scissorState(), base.textures())};
					types.put(item.hologram(), pair);
				}
				Gpu gpu = item.mesh().gpu;
				if (gpu.normal != null) {
					pair[0].drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(gpu.normal, indices, IndexType.INT, 0, 0, gpu.normalQuads * 6, PrimitiveTopology.QUADS), pass);
				}
				if (gpu.fluid != null) {
					pair[1].drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(gpu.fluid, indices, IndexType.INT, 0, 0, gpu.fluidQuads * 6, PrimitiveTopology.QUADS), pass);
				}
			}
			// The outlines go last, over the faces; they are opaque and the depth test sorts them.
			if (maxLineIndices > 0) {
				GpuBuffer lineIndices = lineIndexBuffer(maxLineIndices / 6);
				PreparedRenderType lineBase = RenderTypes.linesDepthBias().prepare();
				Vector4f white = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
				java.util.Map<Hologram, PreparedRenderType> lineTypes = new java.util.IdentityHashMap<>();
				for (Item item : items) {
					Gpu gpu = item.mesh().gpu;
					if (gpu.lines == null) {
						continue;
					}
					PreparedRenderType type = lineTypes.get(item.hologram());
					if (type == null) {
						type = new PreparedRenderType(lineBase.name(), lineBase.pipeline(), lineBase.oitPipelineSet(),
							RenderSystem.getDynamicUniforms().writeTransform(models.get(item.hologram()), white, new Vector3f(), new Matrix4f()), lineBase.scissorState(), lineBase.textures());
						lineTypes.put(item.hologram(), type);
					}
					type.drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(gpu.lines, lineIndices, IndexType.INT, 0, 0, gpu.lineIndexCount, PrimitiveTopology.LINES), pass);
				}
			}
		}
		if (currentFog != null) {
			RenderSystem.setShaderFog(currentFog);
		}
		int quadsDrawn = 0;
		for (Item item : items) {
			quadsDrawn += item.mesh().gpu.normalQuads + item.mesh().gpu.fluidQuads;
		}
		lastQueued = queuedCount;
		lastCells = items.size();
		lastQuadsDrawn = quadsDrawn;
		lastMade = made;
		HologramRenderer.lastDrawMicros = (System.nanoTime() - started) / 1000;
	}

	/** The game's "no fog" settings, or null when they cannot be reached (the world's fog is then left as it is). */
	private static com.mojang.renderpearl.api.buffers.GpuBufferSlice noFog(Minecraft mc) {
		try {
			if (fogField == null) {
				fogField = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("fogRenderer");
				fogField.setAccessible(true);
			}
			return ((net.minecraft.client.renderer.fog.FogRenderer) fogField.get(mc.gameRenderer)).getBuffer(net.minecraft.client.renderer.fog.FogRenderer.FogMode.NONE);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Indices that turn every four vertices into the two triangles of a quad, for at least {@code quads} quads (the game's own shared buffer is not grown when a cell needs more than it holds). */
	private static GpuBuffer indexBuffer(int quads) {
		if (indexQuads < quads) {
			int capacity = Math.max(quads, indexQuads * 2);
			capacity = Math.max(capacity, 16384);
			java.nio.ByteBuffer data = org.lwjgl.system.MemoryUtil.memAlloc(capacity * 6 * 4);
			try {
				for (int q = 0; q < capacity; q++) {
					int v = q * 4;
					data.putInt(v).putInt(v + 1).putInt(v + 2).putInt(v + 2).putInt(v + 3).putInt(v);
				}
				data.flip();
				GpuBuffer old = indexBuffer;
				indexBuffer = RenderSystem.getDevice().createBuffer(() -> "holoprint quad indices", GpuBuffer.USAGE_INDEX, data);
				indexQuads = capacity;
				if (old != null) {
					CLOSE.add(old);
				}
			} finally {
				org.lwjgl.system.MemoryUtil.memFree(data);
			}
		}
		return indexBuffer;
	}

	/** The cell's buffers, made when it has none or when what they were made for has changed. */
	private static Gpu ensure(Item item) {
		SectionMesh mesh = item.mesh();
		Gpu gpu = mesh.gpu;
		int layerStamp = LayerView.mode() == LayerView.Mode.SINGLE ? LayerView.layer() + 1 : 0;
		int builtStamp = hideBuilt ? item.hologram().builtStamp(mesh.stampKey) : 0;
		if (gpu != null && gpu.hidingBuilt == hideBuilt && gpu.builtStamp == builtStamp && gpu.layerStamp == layerStamp && gpu.outlineArgb == outlineArgb) {
			return gpu;
		}
		if (gpu != null) {
			gpu.release();
		}
		gpu = new Gpu();
		gpu.hidingBuilt = hideBuilt;
		gpu.builtStamp = builtStamp;
		gpu.layerStamp = layerStamp;
		gpu.outlineArgb = outlineArgb;
		BitSet built = hideBuilt ? item.hologram().builtCells() : null;
		for (int pass = 0; pass < 2; pass++) {
			boolean fluid = pass == 1;
			int count = 0;
			for (int q = 0; q < mesh.quads; q++) {
				if (mesh.absolute[q] == fluid && keep(mesh, q, built)) {
					count++;
				}
			}
			if (count == 0) {
				continue;
			}
			try (ByteBufferBuilder allocator = new ByteBufferBuilder(count * 4 * DefaultVertexFormat.ENTITY.getVertexSize() + 64)) {
				BufferBuilder builder = new BufferBuilder(allocator, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
				for (int q = 0; q < mesh.quads; q++) {
					if (mesh.absolute[q] != fluid || !keep(mesh, q, built)) {
						continue;
					}
					Direction facing = HologramRenderer.DIRECTIONS[mesh.normal[q]];
					float nx = facing.getStepX();
					float ny = facing.getStepY();
					float nz = facing.getStepZ();
					int colour = 0xFF000000 | mesh.rgb[q];
					for (int v = 0; v < 4; v++) {
						int at = (q * 4 + v) * 5;
						builder.addVertex(mesh.vertices[at], mesh.vertices[at + 1], mesh.vertices[at + 2], colour, mesh.vertices[at + 3], mesh.vertices[at + 4],
							OverlayTexture.NO_OVERLAY, mesh.light[q], nx, ny, nz);
					}
				}
				try (MeshData data = builder.buildOrThrow()) {
					GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "holoprint cell", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
					if (fluid) {
						gpu.fluid = buffer;
						gpu.fluidQuads = count;
					} else {
						gpu.normal = buffer;
						gpu.normalQuads = count;
					}
				}
			}
		}
		if (outlineArgb != 0) {
			buildOutline(mesh, built, gpu);
		}
		mesh.gpu = gpu;
		return gpu;
	}

	/** The twelve edges of every block of the cell that shows a face (the ones two blocks share are drawn once). Fluids are left out: a sea would be a wall of lines. */
	private static void buildOutline(SectionMesh mesh, BitSet built, Gpu gpu) {
		java.util.Set<Long> cells = new java.util.LinkedHashSet<>();
		for (int q = 0; q < mesh.quads; q++) {
			if (!mesh.absolute[q] && keep(mesh, q, built)) {
				cells.add(pack(mesh.block[3 * q], mesh.block[3 * q + 1], mesh.block[3 * q + 2], 0));
			}
		}
		if (cells.isEmpty()) {
			return;
		}
		java.util.Set<Long> edges = new java.util.LinkedHashSet<>();
		for (long cell : cells) {
			int x = unpack(cell, 0);
			int y = unpack(cell, 1);
			int z = unpack(cell, 2);
			for (int a = 0; a <= 1; a++) {
				for (int b = 0; b <= 1; b++) {
					edges.add(pack(x, y + a, z + b, 0));
					edges.add(pack(x + a, y, z + b, 1));
					edges.add(pack(x + a, y + b, z, 2));
				}
			}
		}
		try (ByteBufferBuilder allocator = new ByteBufferBuilder(edges.size() * 2 * DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH.getVertexSize() + 64)) {
			BufferBuilder builder = new BufferBuilder(allocator, PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
			for (long edge : edges) {
				int x = unpack(edge, 0);
				int y = unpack(edge, 1);
				int z = unpack(edge, 2);
				int axis = (int) (edge >>> 60) & 3;
				float dx = axis == 0 ? 1 : 0;
				float dy = axis == 1 ? 1 : 0;
				float dz = axis == 2 ? 1 : 0;
				builder.addVertex(x, y, z).setColor(gpu.outlineArgb).setNormal(dx, dy, dz).setLineWidth(OUTLINE_WIDTH);
				builder.addVertex(x + dx, y + dy, z + dz).setColor(gpu.outlineArgb).setNormal(dx, dy, dz).setLineWidth(OUTLINE_WIDTH);
			}
			try (MeshData data = builder.buildOrThrow()) {
				gpu.lines = RenderSystem.getDevice().createBuffer(() -> "holoprint outline", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
				gpu.lineIndexCount = data.drawState().indexCount();
			}
		}
	}

	/** Three coordinates (20 bits each, relative to the hologram) and an axis in the top bits. */
	private static long pack(int x, int y, int z, int axis) {
		return ((long) axis << 60) | ((long) (x + BIAS) << 40) | ((long) (y + BIAS) << 20) | (long) (z + BIAS);
	}

	private static int unpack(long key, int which) {
		return (int) ((key >>> (which == 0 ? 40 : which == 1 ? 20 : 0)) & 0xFFFFF) - BIAS;
	}

	/** The indices the game draws lines with (each line is four vertices, as two triangles), for at least {@code lines} lines. */
	private static GpuBuffer lineIndexBuffer(int lines) {
		if (lineIndexLines < lines) {
			int capacity = Math.max(Math.max(lines, lineIndexLines * 2), 16384);
			java.nio.ByteBuffer data = org.lwjgl.system.MemoryUtil.memAlloc(capacity * 6 * 4);
			try {
				for (int l = 0; l < capacity; l++) {
					int v = l * 4;
					data.putInt(v).putInt(v + 1).putInt(v + 2).putInt(v + 3).putInt(v + 2).putInt(v + 1);
				}
				data.flip();
				GpuBuffer old = lineIndexBuffer;
				lineIndexBuffer = RenderSystem.getDevice().createBuffer(() -> "holoprint line indices", GpuBuffer.USAGE_INDEX, data);
				lineIndexLines = capacity;
				if (old != null) {
					CLOSE.add(old);
				}
			} finally {
				org.lwjgl.system.MemoryUtil.memFree(data);
			}
		}
		return lineIndexBuffer;
	}

	/** Whether a quad is part of the buffers: its block is not built already (when those are hidden) and its layer is the one shown. */
	private static boolean keep(SectionMesh mesh, int q, BitSet built) {
		if (!LayerView.shows(mesh.block[3 * q + 1])) {
			return false;
		}
		return built == null || !built.get(mesh.cell[q]);
	}
}
