package io.github.dannygt97.holoprint.placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.dannygt97.holoprint.config.HoloprintConfig;
import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.hologram.Verifier;
import io.github.dannygt97.holoprint.mixin.BlockItemAccessor;
import io.github.dannygt97.holoprint.mixin.UseItemInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places the block a hologram asks for, with the state it asks for, when the player uses an item
 * while looking at the hologram ("easy place"), and keeps going to the next missing block while the
 * use button stays down ("fill").
 *
 * <p>Nothing is placed behind the game's back: the click goes through the normal use-item path with
 * a hit result chosen so that the block ends up in the right state (which neighbour it is placed
 * against, which half of that face is clicked and, for blocks that face the player, which way the
 * player looks, sent to the server just for that click). Every block is taken from the inventory and
 * needs the normal reach; in creative mode the block is simply put in the hand, as picking a block does.
 */
public final class EasyPlace {
	/** How long a held button keeps filling after the last block, in ticks. */
	private static final int SESSION_TICKS = 8;
	/** How long a position is left alone after it was tried, in ticks. */
	private static final int RETRY_TICKS = 20;
	/** How long after placing a sign its editor may still open and be filled in. */
	private static final int SIGN_WINDOW_TICKS = 60;
	/** Ticks the server gets to follow a new look direction before the block is placed. */
	private static final int TURN_TICKS = 2;
	/** How far (blocks) around a hologram's box a held button is kept from placing stray blocks. */
	private static final int NEAR = 4;
	private static final int MAX_EVALUATIONS = 30;
	private static final Direction[] SUPPORT_ORDER = {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};
	private static final float[] YAWS = {0.0F, 90.0F, 180.0F, 270.0F};
	private static final float[] PITCHES = {0.0F, 90.0F, -90.0F};

	/** Where to click, what to hold and how to look to place one block. */
	private record Plan(BlockPos target, BlockHitResult hit, float yaw, float pitch, Item item, net.minecraft.world.level.block.Block block, boolean toggle) {}

	/** The block of the hologram the line of sight points at (the one a click would place), for the outline and the label. */
	private static volatile BlockPos lookedCell;
	private static volatile BlockState lookedState;
	private static volatile int lookedMode;
	private static volatile int shownMode;
	private static volatile BlockPos shownCell;
	private static volatile BlockState shownState;

	/** The kind of block the fill in progress keeps placing: the one the player first pointed at. */
	private static net.minecraft.world.level.block.Block fillBlock;

	private static long lastHandled = -100;
	private static long lastPlaced = -100;
	private static long lastNotice = -100;
	private static long lastDriven = -100;
	/** Game tick of the last look direction this class sent to the server: the server's head follows it a tick later. */
	private static long lastRotationSent = -100;
	/** For how many ticks the use button has been held down (0 when it is not): a click with this above 0 is the game repeating a held button. */
	private static int useHeld;
	/** The look direction at the last tick and for how many ticks it has not changed: the server knows a direction that has been still for a while. */
	private static float seenYaw, seenPitch;
	private static int stillTicks;
	private static final Map<Long, Long> RECENT = new HashMap<>();
	/** Spots where a block was just placed, with when that stops counting: a sign placed there gets the hologram's text. */
	private static final Map<Long, Long> PLACED = new HashMap<>();
	/** A door, trapdoor or gate just placed that the hologram has open: once the click that placed it can be followed, it is opened without a second click. */
	private static BlockPos followCell;
	private static long followAt;

	/** Set between {@link #before} and {@link #after} when the click needs the player to look another way. */
	private static boolean spoofed;
	/** The plan whose look direction was already sent to the server, and when. */
	private static Plan turned;
	private static long turnedAt;
	private static boolean clickScheduled;
	private static float realYaw, realPitch, realHead;

	private EasyPlace() {}

	/**
	 * Called before the game handles a use click. Returns true when the click was dealt with (nothing
	 * more should happen), false to let the game go on, possibly with the click aimed at the block to place.
	 */
	public static boolean before(Minecraft mc) {
		spoofed = false;
		HoloprintConfig config = HoloprintConfig.get();
		Player player = mc.player;
		if (!config.easyPlace || !config.showHolograms || player == null || mc.level == null || mc.gameMode == null || Freecam.isActive()
			|| player.isSpectator() || player.isUsingItem() || mc.crosshairPickEntity != null) {
			return false;
		}
		List<Hologram> holograms = new ArrayList<>();
		for (Hologram hologram : HologramRenderer.all()) {
			if (hologram.visible() && hologram.enabled()) {
				holograms.add(hologram);
			}
		}
		if (holograms.isEmpty()) {
			return false;
		}

		long now = mc.level.getGameTime();
		boolean session = config.fillWhileHeld && mc.options.keyUse.isDown() && now - lastHandled <= SESSION_TICKS;
		// The fill places this many blocks per second at most (a setting; never faster than one per game tick).
		long gap = Math.max(1, Math.round(20.0 / config.fillSpeed));
		if (session && now - lastPlaced < gap) {
			lastHandled = now;
			return true;
		}
		RECENT.values().removeIf(until -> until < now);
		PLACED.values().removeIf(until -> until < now);

		Set<Item> missing = new LinkedHashSet<>();
		Plan plan = rayPlan(mc, holograms, missing, now);
		if (plan != null) {
			fillBlock = plan.block();
		}
		if (plan == null && missing.isEmpty() && session) {
			plan = nextPlan(mc, holograms, missing, now);
		}
		if (plan == null) {
			if (!missing.isEmpty()) {
				notifyMissing(mc, missing, now);
				lastHandled = now;
				return true;
			}
			if (session) {
				lastHandled = now;
				return true;
			}
			if (useHeld >= 1 && heldBlock(player).getItem() instanceof BlockItem && (config.fillWhileHeld || nearHologram(mc, holograms))) {
				// A held button only keeps filling the hologram: it never goes on placing blocks wherever it points (with the auto-fill off too, as long as
				// it points at or beside a hologram: the game would repeat the click and put stray blocks around it).
				lastHandled = now;
				return true;
			}
			if (wouldOpenInterface(mc, holograms) && (heldBlock(player).getItem() instanceof BlockItem || useHeld >= 1 || now - lastHandled <= SESSION_TICKS)) {
				// A chest, dispenser, furnace, door, lever... of the hologram, pointed at while placing (a block in hand, or the button held, or right after a
				// placement): the click is not for it, and the game would open its window or flip it. Like Litematica, which leaves the use click to the easy placement
				// while it is on. With an empty hand or another item, a click uses it as usual.
				if (now - lastNotice >= 30) {
					lastNotice = now;
					mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.easy_no_open"), false);
				}
				lastHandled = now;
				return true;
			}
			if (protectedOpenable(mc, holograms)) {
				// A door, trapdoor or gate of the hologram that is already as it should be: a stray click must not change it.
				if (now - lastNotice >= 30) {
					lastNotice = now;
					mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.easy_protected"), false);
				}
				lastHandled = now;
				return true;
			}
			BlockState misplaced = wouldPlaceWrongBlock(mc, holograms);
			if (misplaced != null) {
				// Nothing could be placed where the pointer is, and the game would put the block in hand against what is pointed at,
				// in a spot where the hologram asks for something else.
				if (now - lastNotice >= 30) {
					lastNotice = now;
					mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.easy_wrong", misplaced.getBlock().getName()), false);
				}
				lastHandled = now;
				return true;
			}
			BlockState misstated = wouldPlaceWrongState(mc, holograms);
			if (misstated != null) {
				// The hologram asks for this very block here, but no way of placing it gives the state it asks for (a wall sign with nothing behind it, a chain that
				// would lie down): the game, left alone, would put it down anyway against what the pointer is on, the wrong way round.
				if (now - lastNotice >= 30) {
					lastNotice = now;
					mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.easy_wrong_state", misstated.getBlock().getName()), false);
				}
				lastHandled = now;
				return true;
			}
			if (wouldPlaceOutside(mc, holograms)) {
				// The click is on a block that already is where the hologram asks for it (or on one beside it): the block in hand would go on
				// it or next to it, outside the hologram. With assisted placement on, only what the hologram asks for is placed there.
				if (now - lastNotice >= 30) {
					lastNotice = now;
					mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.easy_outside"), false);
				}
				lastHandled = now;
				return true;
			}
			explain(mc, holograms, now);
			return false;
		}

		// Opening or closing needs no item; it only needs the player to face the right way for a gate (see togglePlan).
		if (!plan.toggle() && !equip(mc, plan.item())) {
			missing.add(plan.item());
			notifyMissing(mc, missing, now);
			lastHandled = now;
			return true;
		}
		lastHandled = now;
		boolean turns = Math.abs(wrap(plan.yaw() - player.getYRot())) > 0.01F || Math.abs(plan.pitch() - player.getXRot()) > 0.01F;
		// Even with no turn to make, the server's head may still be on the look sent just before (a block placed a tick ago, the real look given
		// back): a block that faces where the player looks then waits the same couple of ticks, or it would come out facing the other way.
		boolean uncertain = !turns && !plan.toggle() && needsLook(plan.block().defaultBlockState()) && now - lastRotationSent <= TURN_TICKS;
		if (turns || uncertain) {
			boolean ready = turned != null && turned.target().equals(plan.target()) && turned.yaw() == plan.yaw() && turned.pitch() == plan.pitch() && now - turnedAt >= TURN_TICKS;
			if (!ready) {
				// The server only follows a new look direction a moment later (blocks that face where the player looks
				// read it from the head, which turns with the next tick), so the click waits for it.
				if (turned == null || turned.yaw() != plan.yaw() || turned.pitch() != plan.pitch()) {
					mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(plan.yaw(), plan.pitch(), player.onGround(), player.horizontalCollision));
						lastRotationSent = now;
					turned = plan;
					turnedAt = now;
				}
				clickScheduled = true;
				return true;
			}
			realYaw = player.getYRot();
			realHead = player.getYHeadRot();
			realPitch = player.getXRot();
			spoofed = true;
			player.setYRot(plan.yaw());
			// What a block placed here faces can be read from the head, which would otherwise still look the old way for the game's own prediction.
			player.setYHeadRot(plan.yaw());
			player.setXRot(plan.pitch());
		} else if (turned != null) {
			giveRotationBack(mc);
		}
		lastPlaced = now;
		clickScheduled = false;
		BlockState first = wanted(holograms, plan.target());
		if (!plan.toggle() && first != null && first.hasProperty(BlockStateProperties.SLAB_TYPE) && first.getValue(BlockStateProperties.SLAB_TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE
			&& mc.level.getBlockState(plan.target()).getBlock() != first.getBlock()) {
			// the first half of a double slab: the second half goes on the next click, the client already knows the first one is there
			RECENT.put(plan.target().asLong(), now + 2);
		} else {
			markRecent(mc, plan.target(), now);
		}
		if (!plan.toggle()) {
			PLACED.put(plan.target().asLong(), now + SIGN_WINDOW_TICKS);
			BlockState asked = wanted(holograms, plan.target());
			if (asked != null && openable(asked) && asked.hasProperty(BlockStateProperties.OPEN) && asked.getValue(BlockStateProperties.OPEN)) {
				followCell = plan.target();
				followAt = now;
			}
		}
		mc.hitResult = plan.hit();
		return false;
	}

	/**
	 * The stack a use click would place: the main hand's when it holds a block, else the off hand's (the game goes on to the off hand when the main hand's
	 * item does nothing, so a block carried there would be put down against every rule above).
	 */
	private static ItemStack heldBlock(Player player) {
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		return !(main.getItem() instanceof BlockItem) && off.getItem() instanceof BlockItem ? off : main;
	}

	/**
	 * True when the assisted placement is on and {@code pos} is in or near (within a few blocks of) a visible hologram: there blocks are placed only as the
	 * hologram asks, so a mod that places blocks by itself (the reach-around bridging of Gameplay Tweaks) leaves the spot alone. Read by that mod.
	 */
	public static boolean owns(BlockPos pos) {
		if (!HoloprintConfig.get().easyPlace || !HoloprintConfig.get().showHolograms) {
			return false;
		}
		for (Hologram hologram : HologramRenderer.all()) {
			if (!hologram.visible() || !hologram.enabled()) {
				continue;
			}
			BlockPos o = hologram.origin();
			if (pos.getX() >= o.getX() - NEAR && pos.getY() >= o.getY() - NEAR && pos.getZ() >= o.getZ() - NEAR
				&& pos.getX() < o.getX() + hologram.sizeX() + NEAR && pos.getY() < o.getY() + hologram.sizeY() + NEAR && pos.getZ() < o.getZ() + hologram.sizeZ() + NEAR) {
				return true;
			}
		}
		return false;
	}

	/** The cell a click would fill and what it would be, or null. */
	public static BlockPos targetCell() {
		return shownCell;
	}

	/** True when the cell the pointer is on cannot be filled right now (the outline then says so). */
	public static boolean targetBlocked() {
		return shownMode == BLOCKED;
	}

	private static void refreshTarget(Minecraft mc) {
		shownCell = null;
		shownState = null;
		HoloprintConfig config = HoloprintConfig.get();
		Player player = mc.player;
		if (!config.easyPlace || !config.showHolograms || player == null || mc.level == null || mc.gameMode == null || Freecam.isActive()
			|| player.isSpectator() || mc.gui.screen() != null) {
			return;
		}
		List<Hologram> holograms = new ArrayList<>();
		for (Hologram hologram : HologramRenderer.all()) {
			if (hologram.visible() && hologram.enabled()) {
				holograms.add(hologram);
			}
		}
		if (holograms.isEmpty()) {
			return;
		}
		rayPlan(mc, holograms, new LinkedHashSet<>(), mc.level.getGameTime());
		shownCell = lookedCell;
		shownState = lookedState;
		shownMode = lookedMode;
	}

	private static void renderLabel(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
		BlockState state = shownState;
		if (state == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		// A block that nothing holds up yet (a carpet over water that is not there, a torch with no wall) says so: that is what it waits for.
		BlockPos cell = shownCell;
		boolean unsupported = shownMode == BLOCKED && cell != null && mc.level != null && !state.canSurvive(mc.level, cell);
		Component text = Component.translatable(unsupported ? "holoprint.hud.easy_support" : shownMode == BLOCKED ? "holoprint.hud.easy_blocked" : shownMode == TOGGLE ? "holoprint.hud.easy_toggle" : shownMode == ADJUST ? "holoprint.hud.easy_adjust" : "holoprint.hud.easy_target", state.getBlock().getName());
		int x = (graphics.guiWidth() - mc.font.width(text)) / 2;
		int y = graphics.guiHeight() / 2 + 14;
		graphics.fill(x - 3, y - 2, x + mc.font.width(text) + 3, y + 10, 0x88000000);
		graphics.text(mc.font, text, x, y, shownMode == BLOCKED ? 0xFFBBBBBB : 0xFFFFD166, true);
	}

	/** Runs every tick: finishes a click that was waiting for the server to turn, and never leaves the server looking the wrong way. */
	private static void tick(Minecraft mc) {
		// The button counts as held while the mouse button is still down, even when the game has let go of its key (a screen opened and closed, like the
		// sign editor): otherwise the next repeated click would look like a fresh one, and would open the editor on the sign just placed.
		useHeld = mc.options.keyUse.isDown() || mc.mouseHandler.isRightPressed() ? useHeld + 1 : 0;
		fillSignEditor(mc);
		if (mc.player != null) {
			if (mc.player.getYRot() == seenYaw && mc.player.getXRot() == seenPitch) {
				stillTicks++;
			} else {
				stillTicks = 0;
				seenYaw = mc.player.getYRot();
				seenPitch = mc.player.getXRot();
			}
		}
		if (mc.level != null) {
			long game = mc.level.getGameTime();
			RECENT.values().removeIf(until -> until < game);
		}
		refreshTarget(mc);
		followUp(mc);
		driveFill(mc);
		LayerControl.tick(mc);
		io.github.dannygt97.holoprint.ui.MaterialsPin.tick(mc);
		while (EditorKeys.AUTO_FILL.consumeClick()) {
			HoloprintConfig config = HoloprintConfig.get();
			config.fillWhileHeld = !config.fillWhileHeld;
			config.save();
			if (mc.player != null) {
				mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.auto_fill", Component.translatable(config.fillWhileHeld ? "options.on" : "options.off")), false);
			}
		}
		while (EditorKeys.EASY_PLACE.consumeClick()) {
			HoloprintConfig config = HoloprintConfig.get();
			config.easyPlace = !config.easyPlace;
			config.save();
			if (mc.player != null) {
				mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.easy_place", Component.translatable(config.easyPlace ? "options.on" : "options.off")), false);
			}
		}
		if (turned == null || mc.level == null || mc.player == null) {
			clickScheduled = false;
			turned = null;
			return;
		}
		// The game sends the player's real look direction whenever it changes, which would undo the turn, so it is sent again.
		mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(turned.yaw(), turned.pitch(), mc.player.onGround(), mc.player.horizontalCollision));
		lastRotationSent = mc.level.getGameTime();
		long now = mc.level.getGameTime();
		if (clickScheduled && now - turnedAt >= TURN_TICKS) {
			clickScheduled = false;
			((UseItemInvoker) mc).holoprint$startUseItem();
		}
		if (turned != null && now - turnedAt > 30) {
			giveRotationBack(mc);
		}
	}

	/**
	 * A trapdoor, door or gate that the hologram has open is placed shut (a block is never placed open), so as soon as the spot is free to be clicked
	 * again and the pointer is still on it, the one click that opens it is made here: the player gets the block as the hologram has it from one click.
	 */
	private static void followUp(Minecraft mc) {
		if (followCell == null || mc.level == null) {
			return;
		}
		long now = mc.level.getGameTime();
		if (now - followAt > RETRY_TICKS + 20 || !HoloprintConfig.get().easyPlace) {
			followCell = null;
			return;
		}
		if (shownMode == TOGGLE && followCell.equals(shownCell) && !clickScheduled) {
			followCell = null;
			((UseItemInvoker) mc).holoprint$startUseItem();
		}
	}

	/**
	 * The game repeats a held use button only every 4 ticks (5 blocks per second at the most), so while the fill is going the next
	 * click is made here as soon as the speed setting allows it.
	 */
	private static void driveFill(Minecraft mc) {
		HoloprintConfig config = HoloprintConfig.get();
		if (!config.easyPlace || !config.fillWhileHeld || mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null
			|| !mc.options.keyUse.isDown() || clickScheduled) {
			return;
		}
		long now = mc.level.getGameTime();
		long gap = Math.max(1, Math.round(20.0 / config.fillSpeed));
		if (now - lastHandled <= SESSION_TICKS && now - lastPlaced >= gap && now != lastDriven) {
			lastDriven = now;
			((UseItemInvoker) mc).holoprint$startUseItem();
		}
	}

	/**
	 * A sign placed by the easy placement opens the game's sign editor. If the hologram's sign has text, it is written into the editor
	 * and the editor is closed at once, which sends the text to the server as if it had been typed. Only the front text: the back
	 * text, the wax, the dye and the glow need the player to do them (they are not text).
	 * With the use button still held down (the fill is going on) the editor is closed in any case, empty if the hologram's sign has no text:
	 * it must not get in the way of the blocks that come next. A single click, already let go of, still leaves the editor open to be typed in.
	 */
	private static void fillSignEditor(Minecraft mc) {
		if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen screen) || mc.level == null || PLACED.isEmpty()) {
			return;
		}
		io.github.dannygt97.holoprint.mixin.SignEditScreenAccessor access = (io.github.dannygt97.holoprint.mixin.SignEditScreenAccessor) screen;
		BlockPos pos = access.holoprint$sign().getBlockPos();
		Long until = PLACED.get(pos.asLong());
		if (until == null || until < mc.level.getGameTime()) {
			return;
		}
		PLACED.remove(pos.asLong());
		// opening a screen lets go of every key, so the button is asked of the mouse itself
		boolean held = mc.mouseHandler.isRightPressed() || mc.options.keyUse.isDown();
		for (Hologram hologram : HologramRenderer.all()) {
			net.minecraft.world.level.block.entity.SignBlockEntity ghost = hologram.signAt(mc, pos);
			if (ghost == null) {
				continue;
			}
			List<Component> lines = ghost.getText(access.holoprint$slot()).getMessages(false);
			String[] messages = access.holoprint$messages();
			boolean any = false;
			for (int i = 0; i < messages.length && i < lines.size(); i++) {
				messages[i] = lines.get(i).getString();
				any |= !messages[i].isEmpty();
			}
			if (any || held) {
				mc.setScreenAndShow(null);
			}
			return;
		}
		if (held) {
			mc.setScreenAndShow(null);
		}
	}

	public static void init() {
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("holoprint", "easy_place_target"), EasyPlace::renderLabel);
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("holoprint", "layer"), LayerControl::render);
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("holoprint", "pinned_materials"), io.github.dannygt97.holoprint.ui.MaterialsPin::render);
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("holoprint", "container_contents"), ContainerPreview::render);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(EasyPlace::tick);
	}

	private static void giveRotationBack(Minecraft mc) {
		turned = null;
		clickScheduled = false;
		Player player = mc.player;
		if (player != null && mc.getConnection() != null) {
			mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(), player.onGround(), player.horizontalCollision));
			lastRotationSent = mc.level == null ? lastRotationSent : mc.level.getGameTime();
		}
	}

	/** Called after the game handled the click: gives the player's own rotation back. */
	public static void after(Minecraft mc) {
		if (!spoofed) {
			return;
		}
		turned = null;
		spoofed = false;
		Player player = mc.player;
		if (player != null && mc.getConnection() != null) {
			player.setYRot(realYaw);
			player.setYHeadRot(realHead);
			player.setXRot(realPitch);
			mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(realYaw, realPitch, player.onGround(), player.horizontalCollision));
			lastRotationSent = mc.level == null ? lastRotationSent : mc.level.getGameTime();
		}
	}

	private static float wrap(float angle) {
		return net.minecraft.util.Mth.wrapDegrees(angle);
	}

	// --- what to place ---

	/** The block the holograms ask for at {@code pos}, or null. */
	private static BlockState wanted(List<Hologram> holograms, BlockPos pos) {
		for (Hologram hologram : holograms) {
			BlockState state = hologram.stateAt(pos);
			if (state != null) {
				return state;
			}
		}
		return null;
	}

	/** True when the hologram asks for a block that this position still lacks and that can be placed at all. */
	private static boolean pending(Minecraft mc, BlockState wanted, BlockPos pos) {
		if (!placeable(wanted)) {
			return false;
		}
		BlockState world = mc.level.getBlockState(pos);
		// a double slab with only one of its halves in the world still lacks the other one
		return world.isAir() || (world.canBeReplaced() && world.getBlock() != wanted.getBlock()) || Verifier.halfSlab(wanted, world);
	}

	/** Blocks that have no item of their own, fluids and the second halves that the first half creates are not placed by hand. */
	private static boolean placeable(BlockState wanted) {
		if (wanted.getBlock() instanceof LiquidBlock || !(wanted.getBlock().asItem() instanceof BlockItem) || wanted.getBlock().asItem() == Items.AIR) {
			return false;
		}
		if (wanted.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && wanted.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return false;
		}
		return !(wanted.hasProperty(BlockStateProperties.BED_PART) && wanted.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD);
	}

	/** What the outline and label of the target stand for. */
	private static final int PLACE = 0, TOGGLE = 1, BLOCKED = 2, ADJUST = 3;
	/** How long a repeater, comparator or note block is left alone after a click on it: long enough for the answer to arrive, short enough for several clicks. */
	private static final int SETTING_RETRY_TICKS = 8;
	/** How far (squared) past a spot that cannot be filled yet the search may go for the next one: just the block that holds it up. */
	private static final double FALL_THROUGH_SQ = 4.0;

	/**
	 * The first block along the line of sight that a click can act on now: a missing block to place or a door, trapdoor or gate
	 * to open or close as the hologram asks. A spot that cannot be filled yet (for instance redstone dust whose block below is
	 * not there) lets the search go on only to the block right behind it, never further, so what the outline shows is
	 * always exactly what the click does.
	 */
	private static Plan rayPlan(Minecraft mc, List<Hologram> holograms, Set<Item> missing, long now) {
		lookedCell = null;
		lookedState = null;
		lookedMode = PLACE;
		Player player = mc.player;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double reach = player.blockInteractionRange();
		int x = net.minecraft.util.Mth.floor(eye.x), y = net.minecraft.util.Mth.floor(eye.y), z = net.minecraft.util.Mth.floor(eye.z);
		int stepX = look.x > 0 ? 1 : -1, stepY = look.y > 0 ? 1 : -1, stepZ = look.z > 0 ? 1 : -1;
		double tx = boundary(eye.x, x, look.x), ty = boundary(eye.y, y, look.y), tz = boundary(eye.z, z, look.z);
		double dx = look.x == 0 ? Double.MAX_VALUE : Math.abs(1.0 / look.x);
		double dy = look.y == 0 ? Double.MAX_VALUE : Math.abs(1.0 / look.y);
		double dz = look.z == 0 ? Double.MAX_VALUE : Math.abs(1.0 / look.z);
		BlockPos first = null;
		BlockState firstState = null;
		int evaluations = 0;
		for (int i = 0; i < 128; i++) {
			BlockPos cell = new BlockPos(x, y, z);
			BlockState want = wanted(holograms, cell);
			BlockState world = mc.level.getBlockState(cell);
			boolean recent = RECENT.containsKey(cell.asLong());
			boolean place = !recent && want != null && pending(mc, want, cell);
			boolean toggle = !recent && !place && want != null && toggleNeeded(want, world);
			if (place || toggle) {
				if (first == null) {
					first = cell;
					firstState = want;
				} else if (Vec3.atCenterOf(cell).distanceToSqr(Vec3.atCenterOf(first)) > FALL_THROUGH_SQ) {
					break;
				}
				if (!RECENT.containsKey(cell.asLong()) && evaluations++ < MAX_EVALUATIONS) {
					Plan plan;
					if (toggle) {
						plan = togglePlan(mc, cell, look);
					} else {
						Item item = want.getBlock().asItem();
						if (!has(player, item)) {
							missing.add(item);
							break;
						}
						plan = plan(mc, cell, want, item);
					}
					if (plan != null) {
						lookedCell = cell;
						lookedState = want;
						lookedMode = toggle ? (io.github.dannygt97.holoprint.hologram.Verifier.adjustable(want) ? ADJUST : TOGGLE) : PLACE;
						return plan;
					}
				}
			} else if (!world.isAir() && !world.canBeReplaced()) {
				break;
			}
			double next = Math.min(tx, Math.min(ty, tz));
			if (next > reach) {
				break;
			}
			if (tx <= ty && tx <= tz) {
				x += stepX;
				tx += dx;
			} else if (ty <= tz) {
				y += stepY;
				ty += dy;
			} else {
				z += stepZ;
				tz += dz;
			}
		}
		if (first != null) {
			lookedCell = first;
			lookedState = firstState;
			lookedMode = BLOCKED;
		}
		return null;
	}

	// --- doors, trapdoors and gates ---

	/** True for the blocks that open and close when clicked by hand. */
	private static boolean openable(BlockState state) {
		var block = state.getBlock();
		if (block instanceof DoorBlock door) {
			return door.type().canOpenByHand();
		}
		if (block instanceof TrapDoorBlock) {
			return block != net.minecraft.world.level.block.Blocks.IRON_TRAPDOOR;
		}
		return block instanceof FenceGateBlock || io.github.dannygt97.holoprint.hologram.Verifier.adjustable(state);
	}

	/** True when the world already has the block the hologram asks for, but open where the hologram has it closed or the other way round. */
	private static boolean toggleNeeded(BlockState wanted, BlockState world) {
		if (io.github.dannygt97.holoprint.hologram.Verifier.adjustable(wanted)) {
			// A repeater, comparator or note block of the right kind and facing whose setting differs: one click changes it a step.
			if (world.getBlock() != wanted.getBlock()) {
				return false;
			}
			for (Property<?> property : wanted.getProperties()) {
				if (decisive(wanted, property) && !world.getValue(property).equals(wanted.getValue(property))) {
					return false;
				}
			}
			return io.github.dannygt97.holoprint.hologram.Verifier.settingClicks(wanted, world) > 0;
		}
		if (!openable(wanted) || world.getBlock() != wanted.getBlock() || !wanted.hasProperty(BlockStateProperties.OPEN) || !world.hasProperty(BlockStateProperties.OPEN)) {
			return false;
		}
		if (wanted.getValue(BlockStateProperties.OPEN) == world.getValue(BlockStateProperties.OPEN)) {
			return false;
		}
		// Only when it is the same block facing the same way: a wrongly placed door is not fixed by opening it.
		for (Property<?> property : wanted.getProperties()) {
			if (decisive(wanted, property) && !world.getValue(property).equals(wanted.getValue(property))) {
				return false;
			}
		}
		return true;
	}

	/** The click that opens or closes the block at {@code cell}: a plain use of the block, from the side the player looks at. */
	private static Plan togglePlan(Minecraft mc, BlockPos cell, Vec3 look) {
		Player player = mc.player;
		// A player who sneaks while holding something does not use the block, the game places against it instead.
		if (player.isShiftKeyDown() || !player.isWithinBlockInteractionRange(cell, 0.0)) {
			return null;
		}
		Direction face = Direction.getApproximateNearest(-look.x, -look.y, -look.z);
		Vec3 point = Vec3.atCenterOf(cell).add(face.getStepX() * 0.4, face.getStepY() * 0.4, face.getStepZ() * 0.4);
		BlockState state = mc.level.getBlockState(cell);
		float yaw = player.getYRot();
		// A gate that is opened by someone facing it turns to face away from them, so it is opened by a player looking the way
		// it faces, which keeps it the way the hologram has it.
		if (state.getBlock() instanceof FenceGateBlock && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			yaw = state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
		}
		return new Plan(cell, new BlockHitResult(point, face, cell, false), yaw, player.getXRot(), null, state.getBlock(), true);
	}

	/** Remembers that a spot was just acted on; a door's other half is remembered too, as one click changes both halves. */
	private static void markRecent(Minecraft mc, BlockPos target, long now) {
		BlockState state = mc.level.getBlockState(target);
		RECENT.put(target.asLong(), now + (io.github.dannygt97.holoprint.hologram.Verifier.adjustable(state) ? SETTING_RETRY_TICKS : RETRY_TICKS));
		if (state.getBlock() instanceof DoorBlock) {
			RECENT.put(target.above().asLong(), now + RETRY_TICKS);
			RECENT.put(target.below().asLong(), now + RETRY_TICKS);
		}
	}

	/**
	 * When no placement was found, the click would go on as an ordinary one and put the block in hand where it normally goes.
	 * If that spot is one where the hologram asks for another block, returns what the hologram asks for there (the click is then
	 * cancelled); otherwise null. Spots where the hologram asks for nothing, or for the same block, are left to the game.
	 */
	private static BlockState wouldPlaceWrongBlock(Minecraft mc, List<Hologram> holograms) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return null;
		}
		ItemStack held = heldBlock(mc.player);
		if (!(held.getItem() instanceof BlockItem blockItem)) {
			return null;
		}
		BlockState clickedState = mc.level.getBlockState(hit.getBlockPos());
		// A click on a chest, a crafting table or the like uses it instead of placing anything.
		if (interactive(clickedState) && !mc.player.isShiftKeyDown()) {
			return null;
		}
		BlockPos at = clickedState.canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
		BlockState want = wanted(holograms, at);
		if (want == null || want.getBlock() instanceof LiquidBlock || want.getBlock() == blockItem.getBlock()) {
			return null;
		}
		return want;
	}

	/**
	 * The block a hologram asks for at the spot where the game would put the block in hand, when what the game would put there is not what the hologram has
	 * (another block, or the same one facing, standing or hanging another way), or null when the click would do no harm.
	 */
	private static BlockState wouldPlaceWrongState(Minecraft mc, List<Hologram> holograms) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return null;
		}
		ItemStack held = heldBlock(mc.player);
		if (!(held.getItem() instanceof BlockItem blockItem)) {
			return null;
		}
		if (interactive(mc.level.getBlockState(hit.getBlockPos())) && !mc.player.isShiftKeyDown()) {
			return null;
		}
		BlockPlaceContext context = blockItem.updatePlacementContext(new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, held, hit));
		if (context == null) {
			return null;
		}
		BlockState want = wanted(holograms, context.getClickedPos());
		if (want == null || want.getBlock() instanceof LiquidBlock || want.getBlock().asItem() != held.getItem()) {
			return null;
		}
		BlockItemAccessor accessor = (BlockItemAccessor) blockItem;
		BlockState result = accessor.holoprint$placementState(context);
		if (result == null || !accessor.holoprint$canPlace(context, result)) {
			return null;
		}
		if (result.getBlock() != want.getBlock()) {
			return want;
		}
		for (Property<?> property : want.getProperties()) {
			if (decisive(want, property) && !result.getValue(property).equals(want.getValue(property))) {
				return want;
			}
		}
		return null;
	}

	/** True when the pointer is on a block, or on the spot a block would go, within a few blocks of a hologram's box. */
	private static boolean nearHologram(Minecraft mc, List<Hologram> holograms) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos spot = hit.getBlockPos().relative(hit.getDirection());
		for (Hologram hologram : holograms) {
			BlockPos o = hologram.origin();
			if (spot.getX() >= o.getX() - NEAR && spot.getY() >= o.getY() - NEAR && spot.getZ() >= o.getZ() - NEAR
				&& spot.getX() < o.getX() + hologram.sizeX() + NEAR && spot.getY() < o.getY() + hologram.sizeY() + NEAR && spot.getZ() < o.getZ() + hologram.sizeZ() + NEAR) {
				return true;
			}
		}
		return false;
	}

	/** True when the click would use a block with a window or a switch (see {@link #interactive}) that stands inside a hologram's box. */
	private static boolean wouldOpenInterface(Minecraft mc, List<Hologram> holograms) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || mc.player.isShiftKeyDown()) {
			return false;
		}
		BlockPos pos = hit.getBlockPos();
		if (!interactive(mc.level.getBlockState(pos))) {
			return false;
		}
		for (Hologram hologram : holograms) {
			BlockPos o = hologram.origin();
			if (pos.getX() >= o.getX() && pos.getY() >= o.getY() && pos.getZ() >= o.getZ()
				&& pos.getX() < o.getX() + hologram.sizeX() && pos.getY() < o.getY() + hologram.sizeY() && pos.getZ() < o.getZ() + hologram.sizeZ()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True when the click would put the block in hand against a block of a hologram, or inside a hologram's box, in a spot where the hologram does not
	 * ask for that block: that is outside the hologram. Far from every hologram the game places as usual.
	 */
	private static boolean wouldPlaceOutside(Minecraft mc, List<Hologram> holograms) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return false;
		}
		if (!(heldBlock(mc.player).getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		BlockPos clicked = hit.getBlockPos();
		BlockState clickedState = mc.level.getBlockState(clicked);
		if (interactive(clickedState) && !mc.player.isShiftKeyDown()) {
			return false;
		}
		BlockPos at = clickedState.canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		BlockState want = wanted(holograms, at);
		if (want != null && want.getBlock() == blockItem.getBlock()) {
			return false;
		}
		for (Hologram hologram : holograms) {
			BlockPos o = hologram.origin();
			boolean inBox = at.getX() >= o.getX() && at.getY() >= o.getY() && at.getZ() >= o.getZ()
				&& at.getX() < o.getX() + hologram.sizeX() && at.getY() < o.getY() + hologram.sizeY() && at.getZ() < o.getZ() + hologram.sizeZ();
			if (inBox || hologram.stateAt(clicked) != null) {
				return true;
			}
		}
		return false;
	}

/** True when the click would land on a door, trapdoor or gate that the hologram has built just so: it is not opened or closed by accident. */
	private static boolean protectedOpenable(Minecraft mc, List<Hologram> holograms) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return false;
		}
		BlockState world = mc.level.getBlockState(hit.getBlockPos());
		if (!openable(world)) {
			return false;
		}
		BlockState want = wanted(holograms, hit.getBlockPos());
		return want != null && want.getBlock() == world.getBlock();
	}

	private static double boundary(double origin, int cell, double direction) {
		if (direction == 0) {
			return Double.MAX_VALUE;
		}
		double edge = direction > 0 ? cell + 1 - origin : origin - cell;
		return edge / Math.abs(direction);
	}

	/** The next block to place around the player while the button is held: lowest layer first, nearest first. */
	private static Plan nextPlan(Minecraft mc, List<Hologram> holograms, Set<Item> missing, long now) {
		Player player = mc.player;
		double reach = player.blockInteractionRange();
		int r = (int) Math.ceil(reach) + 1;
		BlockPos centre = player.blockPosition();
		Vec3 eye = player.getEyePosition();
		List<BlockPos> candidates = new ArrayList<>();
		for (int dy = -r - 1; dy <= r; dy++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					BlockPos pos = centre.offset(dx, dy, dz);
					if (RECENT.containsKey(pos.asLong()) || !player.isWithinBlockInteractionRange(pos, 0.0)) {
						continue;
					}
					BlockState want = wanted(holograms, pos);
					if (want != null && (pending(mc, want, pos) || toggleNeeded(want, mc.level.getBlockState(pos))) && (fillBlock == null || want.getBlock() == fillBlock)) {
						candidates.add(pos);
					}
				}
			}
		}
		candidates.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY())
			: Double.compare(a.distToCenterSqr(eye), b.distToCenterSqr(eye)));
		int evaluations = 0;
		for (BlockPos pos : candidates) {
			if (evaluations++ >= MAX_EVALUATIONS) {
				break;
			}
			BlockState want = wanted(holograms, pos);
			Plan plan;
			if (!pending(mc, want, pos)) {
				plan = togglePlan(mc, pos, player.getLookAngle());
			} else {
				Item item = want.getBlock().asItem();
				if (!has(player, item)) {
					missing.add(item);
					continue;
				}
				plan = plan(mc, pos, want, item);
			}
			if (plan != null) {
				return plan;
			}
		}
		return null;
	}

	// --- how to place it ---

	/**
	 * Works out which neighbour to click, where on its face and which way to look so that the item
	 * places {@code wanted} at {@code target}: every way is tried with the game's own placement code and
	 * the one whose result matches the most properties of the wanted state wins.
	 */
	private static Plan plan(Minecraft mc, BlockPos target, BlockState wanted, Item item) {
		Plan plan = planClicks(mc, target, wanted, item);
		return plan != null && item instanceof net.minecraft.world.item.PlaceOnWaterBlockItem ? aimAtWater(mc, target, plan) : plan;
	}

	/**
	 * A lily pad or frogspawn is put where the player's own look meets the water (the item ignores the block it is clicked on), so the look is turned to the
	 * surface of the water right under {@code target}. Null when there is no water source there or the look does not reach it.
	 */
	private static Plan aimAtWater(Minecraft mc, BlockPos target, Plan plan) {
		Player player = mc.player;
		BlockPos water = target.below();
		var fluid = mc.level.getFluidState(water);
		if (!fluid.isSource() || !fluid.is(net.minecraft.tags.FluidTags.WATER) || !mc.level.getBlockState(target).isAir()) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 point = new Vec3(target.getX() + 0.5, water.getY() + fluid.getHeight(mc.level, water) - 0.03, target.getZ() + 0.5);
		Vec3 d = point.subtract(eye);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, horizontal));
		// the game's own look ray, with this look, must end on that water
		Vec3 look = Vec3.directionFromRotation(pitch, yaw);
		BlockHitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(player.blockInteractionRange())),
			net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY, player));
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !hit.getBlockPos().equals(water)) {
			return null;
		}
		return new Plan(plan.target(), plan.hit(), yaw, pitch, plan.item(), plan.block(), false);
	}

	private static Plan planClicks(Minecraft mc, BlockPos target, BlockState wanted, Item item) {
		Player player = mc.player;
		if (!(item instanceof BlockItem blockItem)) {
			return null;
		}
		float yaw0 = player.getYRot(), pitch0 = player.getXRot(), head0 = player.getYHeadRot();
		try {
			// Against a neighbouring block, the way the game normally places a block...
			List<Click> clicks = new ArrayList<>();
			for (Direction toSupport : SUPPORT_ORDER) {
				BlockPos support = target.relative(toSupport);
				BlockState supportState = mc.level.getBlockState(support);
				if (!supportState.isAir() && !supportState.canBeReplaced() && !interactive(supportState) && player.isWithinBlockInteractionRange(support, 0.0)) {
					clicks.add(new Click(support, toSupport.getOpposite()));
				}
			}
			Plan best = search(mc, blockItem, target, wanted, clicks, yaw0, pitch0);
			if (best != null || !HoloprintConfig.get().placeWithoutSupport || !player.isWithinBlockInteractionRange(target, 0.0)) {
				return best;
			}
			// ...and when there is nothing to place it against, by clicking the empty spot itself: the server places
			// a block in a spot it is told to use, supported or not.
			clicks.clear();
			for (Direction face : SUPPORT_ORDER) {
				clicks.add(new Click(target, face));
			}
			return search(mc, blockItem, target, wanted, clicks, yaw0, pitch0);
		} finally {
			player.setYRot(yaw0);
			player.setYHeadRot(head0);
			player.setXRot(pitch0);
		}
	}

	/**
	 * True when the server can be trusted to have the player's current look direction: it has not changed for a couple of ticks (the
	 * game tells the server about a new one only at its next tick, so a click right after a mouse movement would use the old one).
	 */
	private static boolean lookSettled(Minecraft mc) {
		return stillTicks >= 2 && mc.player.getYRot() == seenYaw && mc.player.getXRot() == seenPitch;
	}

	/** True when the state the game gives a placed block depends on where the player looks (which way it faces). */
	private static boolean needsLook(BlockState wanted) {
		for (Property<?> property : wanted.getProperties()) {
			String name = property.getName();
			if (name.equals("facing") || name.equals("orientation") || name.equals("rotation")) {
				return true;
			}
		}
		return false;
	}

	/** A block to click and the face of it that is clicked. */
	private record Click(BlockPos clicked, Direction face) {}

	/** The best way to place {@code wanted} at {@code target} among the given clicks, or null when none gives the asked state. */
	private static Plan search(Minecraft mc, BlockItem blockItem, BlockPos target, BlockState wanted, List<Click> clicks, float yaw0, float pitch0) {
		Player player = mc.player;
		BlockItemAccessor accessor = (BlockItemAccessor) blockItem;
		ItemStack stack = new ItemStack(blockItem);
		int total = wanted.getProperties().size();
		Plan best = null;
		int bestScore = -1;
		// With the look direction still moving the real one is not used: the server may not have it yet, so a centred one is sent.
		int firstRotation = !needsLook(wanted) || lookSettled(mc) ? -1 : 0;
		for (Click click : clicks) {
			Direction face = click.face();
			for (double[] offset : offsets(face, wanted)) {
				Vec3 hitPoint = Vec3.atCenterOf(click.clicked()).add(face.getStepX() * 0.5 + offset[0], face.getStepY() * 0.5 + offset[1], face.getStepZ() * 0.5 + offset[2]);
				BlockHitResult hit = new BlockHitResult(hitPoint, face, click.clicked(), false);
				for (int r = firstRotation; r < YAWS.length * PITCHES.length; r++) {
					float yaw = r < 0 ? yaw0 : YAWS[r % YAWS.length];
					float pitch = r < 0 ? pitch0 : PITCHES[r / YAWS.length];
					player.setYRot(yaw);
					player.setYHeadRot(yaw);
					player.setXRot(pitch);
					BlockPlaceContext context = blockItem.updatePlacementContext(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
					if (context == null || !context.getClickedPos().equals(target)) {
						continue;
					}
					BlockState result = accessor.holoprint$placementState(context);
					if (result == null || !(Verifier.isBuilt(wanted, result) || Verifier.halfSlab(wanted, result)) || !accessor.holoprint$canPlace(context, result)) {
						continue;
					}
					int score = 0;
					boolean right = true;
					for (Property<?> property : wanted.getProperties()) {
						boolean same = result.hasProperty(property) && result.getValue(property).equals(wanted.getValue(property));
						if (same) {
							score++;
						} else if (decisive(wanted, property)) {
							right = false;
							break;
						}
					}
					if (right && score > bestScore) {
						bestScore = score;
						best = new Plan(target, hit, yaw, pitch, blockItem, wanted.getBlock(), false);
						if (score == total) {
							return best;
						}
					}
				}
			}
		}
		return best;
	}

	/**
	 * Properties that the way a block is placed decides and that must come out as asked, or the block would
	 * be wrong for good (which way it faces, which half it is, how it is mounted). The rest (connections,
	 * waterlogging, stair corners, door hinges, power) follow from the neighbours and are left to the game.
	 */
	private static boolean decisive(BlockState wanted, Property<?> property) {
		// a double slab is placed half by half: the half that goes down first may be either
		if (property == BlockStateProperties.SLAB_TYPE && wanted.getValue(BlockStateProperties.SLAB_TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE) {
			return false;
		}
		return io.github.dannygt97.holoprint.hologram.Verifier.decisive(wanted, property);
	}

	/** Places on a face to try: its middle, and for a side face the lower and the upper half (they decide slabs, stairs and trapdoors). */
	private static double[][] offsets(Direction face, BlockState wanted) {
		// A door's hinge side follows from which side of the face is clicked, so for doors both sides are tried too.
		boolean sides = wanted.hasProperty(BlockStateProperties.DOOR_HINGE);
		if (face.getAxis().isVertical()) {
			return sides ? new double[][] {{-0.3, 0.0, -0.3}, {0.3, 0.0, -0.3}, {-0.3, 0.0, 0.3}, {0.3, 0.0, 0.3}} : new double[][] {{0.0, 0.0, 0.0}};
		}
		if (sides) {
			double ax = face.getAxis() == Direction.Axis.X ? 0.0 : 0.3, az = face.getAxis() == Direction.Axis.X ? 0.3 : 0.0;
			return new double[][] {{-ax, -0.25, -az}, {ax, -0.25, az}, {-ax, 0.25, -az}, {ax, 0.25, az}};
		}
		return new double[][] {{0.0, -0.25, 0.0}, {0.0, 0.25, 0.0}};
	}

	/** Blocks that do something when clicked, so a click on them would not place anything. */
	private static boolean interactive(BlockState state) {
		var block = state.getBlock();
		return block instanceof EntityBlock || block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
			|| block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof BedBlock || block instanceof NoteBlock
			|| block instanceof DiodeBlock || block instanceof ComposterBlock || block instanceof AbstractCauldronBlock || block instanceof CakeBlock
			|| block instanceof FlowerPotBlock || block instanceof RespawnAnchorBlock || block instanceof CraftingTableBlock || block instanceof AnvilBlock
			|| block instanceof GrindstoneBlock || block instanceof StonecutterBlock || block instanceof LoomBlock || block instanceof CartographyTableBlock
			|| block instanceof SmithingTableBlock || block instanceof BellBlock;
	}

	// --- the inventory ---

	private static boolean has(Player player, Item item) {
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		return slotOf(player.getInventory(), item) >= 0;
	}

	private static int slotOf(Inventory inventory, Item item) {
		List<ItemStack> items = inventory.getNonEquipmentItems();
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			if (!stack.isEmpty() && stack.getItem() == item) {
				return i;
			}
		}
		return -1;
	}

	/** Puts {@code item} in the main hand: selects its hotbar slot, swaps it in from the inventory or, in creative mode, creates it. */
	private static boolean equip(Minecraft mc, Item item) {
		Player player = mc.player;
		Inventory inventory = player.getInventory();
		if (inventory.getSelectedItem().getItem() == item) {
			return true;
		}
		int slot = slotOf(inventory, item);
		if (slot >= 0 && slot < 9) {
			inventory.setSelectedSlot(slot);
			// The game only tells the server about a changed hotbar slot at its next tick, which is after this click: without this
			// the server would still have the previous item in hand and place that one instead.
			mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
			return true;
		}
		if (slot >= 9) {
			mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, inventory.getSelectedSlot(), ContainerInput.SWAP, player);
			return inventory.getSelectedItem().getItem() == item;
		}
		if (player.hasInfiniteMaterials()) {
			ItemStack made = new ItemStack(item);
			mc.gameMode.handleCreativeModeItemAdd(made, 36 + inventory.getSelectedSlot());
			// The server does not answer a creative slot change, so the client's own copy is changed too: otherwise the next click still
			// thinks the old item is in hand, and asking for that one again would place whatever was made last.
			inventory.setItem(inventory.getSelectedSlot(), made);
			return true;
		}
		return false;
	}

	/**
	 * A click that looked at a block the hologram asks for but placed nothing says why, in the action bar:
	 * the block is out of reach, or there is no block next to it to place it against.
	 */
	private static void explain(Minecraft mc, List<Hologram> holograms, long now) {
		if (now - lastNotice < 30) {
			return;
		}
		Player player = mc.player;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double far = 96.0;
		int x = net.minecraft.util.Mth.floor(eye.x), y = net.minecraft.util.Mth.floor(eye.y), z = net.minecraft.util.Mth.floor(eye.z);
		int stepX = look.x > 0 ? 1 : -1, stepY = look.y > 0 ? 1 : -1, stepZ = look.z > 0 ? 1 : -1;
		double tx = boundary(eye.x, x, look.x), ty = boundary(eye.y, y, look.y), tz = boundary(eye.z, z, look.z);
		double dx = look.x == 0 ? Double.MAX_VALUE : Math.abs(1.0 / look.x);
		double dy = look.y == 0 ? Double.MAX_VALUE : Math.abs(1.0 / look.y);
		double dz = look.z == 0 ? Double.MAX_VALUE : Math.abs(1.0 / look.z);
		double vanilla = mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? mc.hitResult.getLocation().distanceTo(eye) : Double.MAX_VALUE;
		for (int i = 0; i < 400; i++) {
			BlockPos cell = new BlockPos(x, y, z);
			BlockState want = wanted(holograms, cell);
			BlockState world = mc.level.getBlockState(cell);
			double distance = Vec3.atCenterOf(cell).distanceTo(eye);
			if (distance > vanilla) {
				return;
			}
			if (want != null && pending(mc, want, cell)) {
				lastNotice = now;
				String key;
				if (!player.isWithinBlockInteractionRange(cell, 0.0)) {
					mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.easy_far", (int) Math.round(distance), (int) Math.round(player.blockInteractionRange())), false);
					return;
				}
				boolean support = false;
				for (Direction d : Direction.values()) {
					BlockState next = mc.level.getBlockState(cell.relative(d));
					support |= !next.isAir() && !next.canBeReplaced() && !interactive(next);
				}
				key = support || HoloprintConfig.get().placeWithoutSupport ? "holoprint.message.easy_state" : "holoprint.message.easy_support";
				mc.gui.hud.setOverlayMessage(Component.translatable(key), false);
				return;
			} else if (!world.isAir() && !world.canBeReplaced()) {
				return;
			}
			double next = Math.min(tx, Math.min(ty, tz));
			if (next > far) {
				return;
			}
			if (tx <= ty && tx <= tz) {
				x += stepX;
				tx += dx;
			} else if (ty <= tz) {
				y += stepY;
				ty += dy;
			} else {
				z += stepZ;
				tz += dz;
			}
		}
	}

	private static void notifyMissing(Minecraft mc, Set<Item> missing, long now) {
		if (now - lastNotice < 30) {
			return;
		}
		lastNotice = now;
		List<String> names = new ArrayList<>();
		for (Item item : missing) {
			if (names.size() < 3) {
				names.add(new ItemStack(item).getHoverName().getString());
			}
		}
		String more = missing.size() > 3 ? " +" + (missing.size() - 3) : "";
		mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.missing_items", String.join(", ", names) + more), false);
	}
}
