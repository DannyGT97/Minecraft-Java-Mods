package io.github.dannygt97.holoprint.placement;

import java.util.ArrayList;
import java.util.List;

import io.github.dannygt97.holoprint.client.ControllerSupport;
import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.ui.EditorScreen;
import io.github.dannygt97.holoprint.ui.HotbarTools;
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
		io.github.dannygt97.holoprint.ui.Notices.init();
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
		atBuild = false;
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

	/** True while the key that changes the mode has stopped on Build (a tool of the hotbar that is used with a click, not a mode): the mode itself is still the last one. */
	private static boolean atBuild;

	/**
	 * The next mode in the ring; Move is skipped while there is nothing to move, and after the last mode comes the first. With the tools on the hotbar,
	 * Build is one more stop after the last mode: the highlight goes to it and a click builds.
	 */
	public static void cycleMode(Minecraft mc) {
		if (!atBuild && mode == EditorMode.values()[EditorMode.values().length - 1] && HotbarTools.enabled() && HotbarTools.buildUsable()) {
			atBuild = true;
			HotbarTools.focusBuild();
			return;
		}
		atBuild = false;
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
		atBuild = false;
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

	/** Escape, while the editor is on with no window open: true when it cancelled something (so the pause menu does not open). */
	public static boolean escape(Minecraft mc) {
		return active && mc.gui.screen() == null && tool(mode).escape(mc);
	}

	/** A Build of {@code hologram} is starting: if it is being carried, it is put down where it is. */
	public static void pinPlacement(Hologram hologram) {
		MOVE.pin(hologram);
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
		// The hotbar was left on Load; the click that puts the new placement down has to reach Move.
		io.github.dannygt97.holoprint.ui.HotbarTools.holding();
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

	/** True when {@code key} was pressed since the last look; all its pending presses are used up. */
	private static boolean pressed(net.minecraft.client.KeyMapping key) {
		boolean any = false;
		while (key.consumeClick()) {
			any = true;
		}
		return any;
	}

	/**
	 * The player's own actions that the editor has no use for (dropping what the hand holds, swapping hands) do nothing while the free camera is on:
	 * a button shared with the editor (the controller's B drops items too) would otherwise throw the item away, or do it as soon as the editor ends.
	 */
	public static void dropGameKeys(Minecraft mc) {
		if (Freecam.isActive()) {
			pressed(mc.options.keyDrop);
			pressed(mc.options.keySwapOffhand);
		}
	}

	private static void tick(Minecraft mc) {
		EditorKeys.migrateOldDefaults(mc);
		// Dying (and coming back in the bed) creates a new player: the editor ends, giving the view and the controls to the one that exists now.
		if (active && Freecam.stale(mc)) {
			stop(mc);
		}
		Freecam.keepInput(mc);
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
			boolean open = EditorKeys.EDITOR.consumeClick();
			open |= pressed(EditorKeys.PAD_BACK);
			if (open && mc.gui.screen() == null) {
				start(mc);
			}
			EditorKeys.drainUnused();
			return;
		}
		dropGameKeys(mc);
		Screen screen = mc.gui.screen();
		if (screen != null && !(screen instanceof EditorScreen)) {
			EditorKeys.drainUnused();
			return;
		}

		if (EditorKeys.EDITOR.consumeClick()) {
			stop(mc);
			return;
		}
		// The controller's back button: cancels what the tool is doing and, with nothing to cancel, closes the editor.
		if (pressed(EditorKeys.PAD_BACK) && screen == null && !escape(mc)) {
			stop(mc);
			return;
		}
		// With the tools on the hotbar the menu is opened from there (slot 9); Tab only opens it the old way without them.
		if (EditorKeys.INTERFACE.consumeClick() && screen == null && !io.github.dannygt97.holoprint.ui.HotbarTools.enabled()) {
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
		// A controller has no Ctrl: with one in use the copy, cut and paste buttons act on their own.
		boolean ctrl = mc.hasControlDown() || ControllerSupport.active();
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
