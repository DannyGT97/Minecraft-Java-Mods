package io.github.dannygt97.holoprint.placement;

import java.util.ArrayList;
import java.util.List;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.ui.EditorScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The editor: a free camera with several editing modes. This class owns what is common to all of
 * them (opening and closing, the current mode, the pointer ray, the shared keys) and hands every
 * click and tick to the tool of the current mode. Switching mode never discards anything: the
 * hologram, the selection and the camera stay as they were.
 */
public final class Editor {
	/** How far a pointer ray reaches into the world. */
	private static final double REACH = 256.0;
	/** How far in front of the camera a ray that hits nothing is taken to point. */
	private static final double AIR_DISTANCE = 20.0;

	private static final MoveTool MOVE = new MoveTool();
	private static final SelectTool SELECT = new SelectTool();
	private static final CopyTool COPY = new CopyTool();
	private static final DeleteTool DELETE = new DeleteTool();

	private static boolean active;
	private static EditorMode mode = EditorMode.MOVE;

	private Editor() {}

	public static void init() {
		EditorKeys.init();
		io.github.dannygt97.holoprint.ui.HotbarTools.init();
		ClientTickEvents.END_CLIENT_TICK.register(Editor::tick);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("holoprint", "editor_help"), EditorHud::render);
	}

	public static boolean isActive() {
		return active;
	}

	public static EditorMode mode() {
		return mode;
	}

	static EditorTool tool(EditorMode which) {
		return switch (which) {
			case MOVE -> MOVE;
			case SELECT -> SELECT;
			case COPY -> COPY;
			case DELETE -> DELETE;
		};
	}

	static List<Component> legend() {
		return new ArrayList<>(tool(mode).legend());
	}

	/** Opens the editor: Move when a hologram is loaded, Select area when there is none. */
	public static void start(Minecraft mc) {
		if (active || mc.player == null || mc.level == null) {
			return;
		}
		Freecam.start(mc);
		active = true;
		mode = HologramRenderer.active() != null ? EditorMode.MOVE : EditorMode.SELECT;
		// Coming back to the editor never makes a placed hologram follow the camera again: it stays where it was put.
		tool(mode).enter(mc, false);
	}

	/** Leaves the editor with everything where it is now. */
	public static void stop(Minecraft mc) {
		if (!active) {
			return;
		}
		tool(mode).leave(mc);
		active = false;
		if (mc.gui.screen() instanceof EditorScreen) {
			mc.setScreenAndShow(null);
		}
		Freecam.stop(mc);
	}

	/** The next mode in the ring; Move is skipped while there is nothing to move, and after the last mode comes the first. */
	public static void cycleMode(Minecraft mc) {
		EditorMode next = mode.next();
		if (next == EditorMode.MOVE && HologramRenderer.active() == null) {
			next = next.next();
		}
		setMode(mc, next);
	}

	/** The placement that Move works on has changed (another one was picked in the list). */
	public static void activePlacementChanged(Minecraft mc) {
		if (active && mode == EditorMode.MOVE) {
			tool(mode).enter(mc, false);
		}
	}

	/** Changes the editing mode. Nothing is discarded; Move needs a hologram. */
	public static void setMode(Minecraft mc, EditorMode wanted) {
		if (!active || wanted == mode) {
			return;
		}
		if (wanted == EditorMode.MOVE && HologramRenderer.active() == null) {
			message(mc, Component.translatable("holoprint.message.no_hologram"));
			return;
		}
		tool(mode).leave(mc);
		mode = wanted;
		tool(mode).enter(mc, false);
	}

	public static void onPrimaryClick() {
		Minecraft mc = Minecraft.getInstance();
		if (active) {
			tool(mode).primaryClick(mc, currentRay(mc));
		}
	}

	public static void onSecondaryClick() {
		Minecraft mc = Minecraft.getInstance();
		if (active) {
			tool(mode).secondaryClick(mc, currentRay(mc));
		}
	}

	/** A new placement was added: the editor moves to Move mode and the placement follows the pointer. */
	static void showNewPlacementInternal(Minecraft mc) {
		if (!active) {
			return;
		}
		if (mode != EditorMode.MOVE) {
			setMode(mc, EditorMode.MOVE);
		}
		// A new placement always starts out following the pointer, whichever mode the editor was in.
		if (mode == EditorMode.MOVE) {
			tool(mode).enter(mc, true);
		}
	}

	public static void showNewPlacement(Minecraft mc) {
		showNewPlacementInternal(mc);
	}

	/** The direction the pointer aims in: the camera's look while the mouse turns it, the cursor position while it is free. */
	static Vec3 currentRay(Minecraft mc) {
		if (mc.gui.screen() instanceof EditorScreen editor) {
			return editor.pointerRay();
		}
		return Freecam.lookVector();
	}

	/** The block the ray hits, or null. */
	static BlockHitResult hit(Minecraft mc, Vec3 ray) {
		Vec3 from = Freecam.eyePosition();
		BlockHitResult hit = mc.level.clip(new ClipContext(from, from.add(ray.scale(REACH)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, Freecam.entity()));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	/** The block the ray hits, or a point in the air along it when it hits nothing. */
	static BlockPos targetBlock(Minecraft mc, Vec3 ray) {
		BlockHitResult hit = hit(mc, ray);
		return hit != null ? hit.getBlockPos() : airPoint(ray);
	}

	/** The empty block next to the face the ray hits, or a point in the air along it. */
	static BlockPos targetNextTo(Minecraft mc, Vec3 ray) {
		BlockHitResult hit = hit(mc, ray);
		return hit != null ? hit.getBlockPos().relative(hit.getDirection()) : airPoint(ray);
	}

	private static BlockPos airPoint(Vec3 ray) {
		return BlockPos.containing(Freecam.eyePosition().add(ray.scale(AIR_DISTANCE)));
	}

	/** Where something held by the pointer goes: the block, and how far from the camera it is. */
	record Aim(BlockPos pos, double distance, boolean grounded) {}

	/** How far below a point in the air the ground still counts as "at ground level", so the hologram settles on it. */
	private static final double SNAP_DISTANCE = 6.0;

	/**
	 * Where a hologram held at {@code held} blocks from the camera goes when the pointer aims along
	 * {@code ray}. It stays at that distance from the camera, so a far-away hill behind it cannot drag
	 * it away (the hologram itself does not block the ray, so aiming at it would otherwise push it back
	 * and back). Terrain that is nearer than that distance wins and the hologram sits on it, and a point
	 * in the air close above the ground drops onto the ground. When there is no ground near and the pointer
	 * meets terrain further on, the hologram settles there.
	 */
	static Aim aimHeld(Minecraft mc, Vec3 ray, double held) {
		BlockHitResult hit = hit(mc, ray);
		if (hit != null) {
			double distance = hit.getLocation().distanceTo(Freecam.eyePosition());
			if (distance <= held + 1.0) {
				return new Aim(hit.getBlockPos().relative(hit.getDirection()), distance, true);
			}
		}
		BlockPos point = BlockPos.containing(Freecam.eyePosition().add(ray.scale(held)));
		BlockPos ground = groundBelow(mc, point, SNAP_DISTANCE);
		if (ground.equals(point) && hit != null) {
			// Hanging in the air with no ground near, but the pointer does meet terrain further along (flying high, looking at the
			// land ahead): the hologram settles on that terrain, as it would on the ground.
			return new Aim(hit.getBlockPos().relative(hit.getDirection()), hit.getLocation().distanceTo(Freecam.eyePosition()), true);
		}
		return new Aim(ground, held, !ground.equals(point));
	}

	/** The block position just above the first ground found within {@code range} blocks below {@code from}, or {@code from} itself when there is none. */
	public static BlockPos groundBelow(Minecraft mc, BlockPos from, double range) {
		Vec3 top = Vec3.atCenterOf(from);
		BlockHitResult hit = mc.level.clip(new ClipContext(top, top.add(0.0, -range, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Freecam.isActive() ? Freecam.entity() : mc.player));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().above() : from;
	}

	/**
	 * The ground under {@code point}, looked for from the camera's height down, so that a point that
	 * ended up inside the terrain (the camera looking steeply down) still finds the surface above it.
	 * The point itself when there is no ground within 64 blocks.
	 */
	public static BlockPos groundNear(Minecraft mc, BlockPos point) {
		int startY = Math.max(point.getY(), Mth.floor(Freecam.isActive() ? Freecam.eyePosition().y : mc.player.getEyeY()));
		Vec3 top = Vec3.atCenterOf(new BlockPos(point.getX(), startY, point.getZ()));
		BlockHitResult hit = mc.level.clip(new ClipContext(top, top.add(0.0, -64.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Freecam.isActive() ? Freecam.entity() : mc.player));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().above() : point;
	}

	static void message(Minecraft mc, Component text) {
		if (mc.player != null) {
			mc.player.sendSystemMessage(text);
		}
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			if (active) {
				active = false;
				Freecam.stop(mc);
			}
			EditorKeys.drainUnused();
			return;
		}
		SelectionActions.tick(mc);
		if (!active) {
			if (EditorKeys.EDITOR.consumeClick() && mc.gui.screen() == null) {
				start(mc);
			}
			EditorKeys.drainUnused();
			return;
		}
		Screen screen = mc.gui.screen();
		if (screen != null && !(screen instanceof EditorScreen)) {
			EditorKeys.drainUnused();
			return;
		}

		if (EditorKeys.EDITOR.consumeClick()) {
			stop(mc);
			return;
		}
		if (EditorKeys.INTERFACE.consumeClick() && screen == null) {
			EditorScreen.open(mc);
		}
		if (EditorKeys.MATERIALS.consumeClick()) {
			if (screen == null) {
				EditorScreen.open(mc);
			}
			if (mc.gui.screen() instanceof EditorScreen editor) {
				editor.toggleMaterials();
			}
		}
		while (EditorKeys.SPEED_DOWN.consumeClick()) {
			Freecam.changeSpeed(-1);
		}
		while (EditorKeys.SPEED_UP.consumeClick()) {
			Freecam.changeSpeed(1);
		}
		while (EditorKeys.MODE.consumeClick()) {
			cycleMode(mc);
		}
		if (EditorKeys.CANCEL.consumeClick()) {
			tool(mode).cancel(mc);
			if (!active) {
				return;
			}
		}
		boolean ctrl = mc.hasControlDown();
		while (EditorKeys.COPY.consumeClick()) {
			if (ctrl) SelectionActions.copy(mc);
		}
		while (EditorKeys.CUT.consumeClick()) {
			if (ctrl) SelectionActions.cut(mc);
		}
		while (EditorKeys.PASTE.consumeClick()) {
			if (ctrl) SelectionActions.paste(mc);
		}
		while (EditorKeys.DELETE.consumeClick()) {
			SelectionActions.delete(mc);
		}
		while (EditorKeys.SAVE.consumeClick()) {
			SelectionActions.save(mc);
		}
		while (EditorKeys.BUILD.consumeClick()) {
			HologramBuilder.request(mc, currentRay(mc));
		}

		tool(mode).tick(mc, currentRay(mc));
		EditorKeys.drainUnused();
	}
}
