package io.github.dannygt97.holoprint.hologram;

import io.github.dannygt97.holoprint.config.HoloprintConfig;
import io.github.dannygt97.holoprint.placement.Editor;
import io.github.dannygt97.holoprint.placement.EditorMode;
import io.github.dannygt97.holoprint.selection.Selection;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws the selected area as a box outline with a faint fill. */
public final class SelectionRenderer {
	private static final float LINE_WIDTH = 3.0F;

	private SelectionRenderer() {}

	private static int lighten(int rgb) {
		int r = (rgb >> 16 & 0xFF) + (255 - (rgb >> 16 & 0xFF)) / 2;
		int g = (rgb >> 8 & 0xFF) + (255 - (rgb >> 8 & 0xFF)) / 2;
		int b = (rgb & 0xFF) + (255 - (rgb & 0xFF)) / 2;
		return r << 16 | g << 8 | b;
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			// The block of the hologram that a use click would place.
			net.minecraft.core.BlockPos cell = io.github.dannygt97.holoprint.placement.EasyPlace.targetCell();
			if (cell != null && !Editor.isActive()) {
				drawBox(context, new AABB(cell).inflate(0.002), io.github.dannygt97.holoprint.placement.EasyPlace.targetBlocked() ? 0xFF9A9A9A : 0xFFFFD166);
			}
			selectedPlacement(context);
			differences(context);
			if (!Editor.isActive() || Editor.mode() != EditorMode.SELECT) {
				return;
			}
			AABB box = Selection.box();
			if (box == null) {
				return;
			}
			int rgb = HoloprintConfig.get().selectionColor;
			// While the second corner is still following the pointer the box is drawn paler.
			drawBox(context, box, Selection.isComplete() ? 0xFF000000 | rgb : 0xFF000000 | lighten(rgb));
		});
	}

/** The colour of the box around the placement that is selected. */
private static final int ACTIVE_COLOR = 0xFFFFD166;

/**
 * The placement that is selected (the one the editor works on, or a finished one picked in the list: only one at a time) gets a gold box around it and its
 * name floating above, in the editor and out of it, however far it is. Like Litematica's boxes it is not tied to a tool; unlike them it does not vanish
 * past the far plane of the camera: a box farther than that is drawn smaller and nearer, along the same lines of sight from the camera, so it covers the same
 * part of the screen.
 */
private static void selectedPlacement(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
	Hologram selected = HologramRenderer.selected();
	if (!HoloprintConfig.get().showHolograms || selected == null || !selected.visible() || selected.sizeX() == 0) {
		return;
	}
	net.minecraft.core.BlockPos o = selected.origin();
	AABB box = new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + selected.sizeX(), o.getY() + selected.sizeY(), o.getZ() + selected.sizeZ()).inflate(0.03);
	Vec3 cam = context.levelState().cameraRenderState.pos;
	float far = context.levelState().cameraRenderState.depthFar;
	// the farthest corner decides how much everything is brought closer
	double farthest = 0.0;
	for (int i = 0; i < 8; i++) {
		double x = (i & 1) == 0 ? box.minX : box.maxX, y = (i & 2) == 0 ? box.minY : box.maxY, z = (i & 4) == 0 ? box.minZ : box.maxZ;
		farthest = Math.max(farthest, Math.sqrt((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z)));
	}
	// Past the far plane nothing is drawn, and where the fog begins the gold turns into the colour of the sky, so a box farther than a bit more than half the
	// render distance is brought to that distance (the same lines of sight, so the same place on the screen).
	double limit = Math.min(far > 0 ? far * 0.9 : Double.MAX_VALUE, net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0 * 0.55);
	double k = farthest > limit ? limit / farthest : 1.0;
	AABB drawn = k == 1.0 ? box : new AABB(cam.x + (box.minX - cam.x) * k, cam.y + (box.minY - cam.y) * k, cam.z + (box.minZ - cam.z) * k,
		cam.x + (box.maxX - cam.x) * k, cam.y + (box.maxY - cam.y) * k, cam.z + (box.maxZ - cam.z) * k);
	drawBox(context, drawn, ACTIVE_COLOR);
	double cx = ((box.minX + box.maxX) / 2.0 - cam.x) * k, cy = (box.maxY + 0.7 - cam.y) * k, cz = ((box.minZ + box.maxZ) / 2.0 - cam.z) * k;
	double distance = Math.sqrt(cx * cx + cy * cy + cz * cz) / k;
	// The name keeps a readable size from far away: it grows with the distance (and the nearer it is drawn, the smaller it has to be to look the same).
	float scale = (float) (Math.max(1.6, Math.min(12.0, distance / 10.0)) * k);
	int index = HologramRenderer.all().indexOf(selected);
	net.minecraft.network.chat.Component name = net.minecraft.network.chat.Component.literal(io.github.dannygt97.holoprint.ui.panel.PlacementsPanel.nameOf(selected, Math.max(0, index)));
	com.mojang.blaze3d.vertex.PoseStack pose = context.poseStack();
	pose.pushPose();
	pose.translate(cx, cy, cz);
	pose.scale(scale, scale, scale);
	context.submitNodeCollector().submitNameTag(pose, Vec3.ZERO, 0, name, false, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, context.levelState().cameraRenderState);
	pose.popPose();
}

private static final int WRONG_COLOR = 0xFFFF4040;
private static final int WRONG_STATE_COLOR = 0xFFFFA040;
private static final int EXTRA_COLOR = 0xFF4090FF;
/** The right block whose setting differs (repeater delay, comparator mode, note). */
private static final int SETTING_COLOR = 0xFFFFEE33;
/** The most outlines drawn in one frame (the nearest ones are found by skipping what is too far). */
private static final int MAX_MARKS = 1500;

/** Outlines what the last check found wrong: another block (red), the right block turned wrong (orange) and, if asked, blocks where nothing is asked (blue). */
private static void differences(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
	HoloprintConfig config = HoloprintConfig.get();
	if (!config.showDifferences || !config.showHolograms) {
		return;
	}
	Vec3 cam = context.levelState().cameraRenderState.pos;
	double range = config.drawDistance;
	for (Hologram hologram : HologramRenderer.all()) {
		io.github.dannygt97.holoprint.hologram.Verifier.Result result = hologram.verification();
		if (!hologram.visible() || !hologram.enabled() || result == null || !result.matches(hologram.origin(), hologram.quarterTurns(), hologram.mirrored())) {
			continue;
		}
		int baseY = hologram.origin().getY();
		marks(context, result.wrong, WRONG_COLOR, cam, range, baseY);
		marks(context, result.wrongState, WRONG_STATE_COLOR, cam, range, baseY);
		marks(context, result.wrongSetting, SETTING_COLOR, cam, range, baseY);
		if (config.showExtra) {
			marks(context, result.extra, EXTRA_COLOR, cam, range, baseY);
		}
	}
}

private static void marks(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context, long[] positions, int color, Vec3 cam, double range, int baseY) {
	if (positions.length == 0) {
		return;
	}
	double rangeSquared = range * range;
	context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), (pose, consumer) -> {
		int drawn = 0;
		for (long packed : positions) {
				if (!LayerView.shows(net.minecraft.core.BlockPos.getY(packed) - baseY)) {
				continue;
				}
				double dx = net.minecraft.core.BlockPos.getX(packed) + 0.5 - cam.x, dy = net.minecraft.core.BlockPos.getY(packed) + 0.5 - cam.y, dz = net.minecraft.core.BlockPos.getZ(packed) + 0.5 - cam.z;
			if (dx * dx + dy * dy + dz * dz > rangeSquared) {
				continue;
			}
			if (drawn++ >= MAX_MARKS) {
				break;
			}
			float x1 = (float) (dx - 0.5 - 0.002), y1 = (float) (dy - 0.5 - 0.002), z1 = (float) (dz - 0.5 - 0.002);
			float x2 = x1 + 1.004F, y2 = y1 + 1.004F, z2 = z1 + 1.004F;
			float[][] corners = {
				{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2},
				{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}
			};
			int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
			for (int[] edge : edges) {
				float[] from = corners[edge[0]];
				float[] to = corners[edge[1]];
				float nx = to[0] - from[0], ny = to[1] - from[1], nz = to[2] - from[2];
				float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				nx /= length; ny /= length; nz /= length;
				consumer.addVertex(pose, from[0], from[1], from[2]).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
				consumer.addVertex(pose, to[0], to[1], to[2]).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
			}
		}
	});
}

private static void drawBox(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context, AABB box, int color) {
		Vec3 cam = context.levelState().cameraRenderState.pos;
		float x1 = (float) (box.minX - cam.x), y1 = (float) (box.minY - cam.y), z1 = (float) (box.minZ - cam.z);
		float x2 = (float) (box.maxX - cam.x), y2 = (float) (box.maxY - cam.y), z2 = (float) (box.maxZ - cam.z);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), (pose, consumer) -> {
			float[][] corners = {
				{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2},
				{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}
			};
			// Four edges of the bottom, four of the top and four verticals.
			int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
			for (int[] edge : edges) {
				float[] from = corners[edge[0]];
				float[] to = corners[edge[1]];
				float nx = to[0] - from[0], ny = to[1] - from[1], nz = to[2] - from[2];
				float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				nx /= length; ny /= length; nz /= length;
				consumer.addVertex(pose, from[0], from[1], from[2]).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
				consumer.addVertex(pose, to[0], to[1], to[2]).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
			}
		});
	}
}
