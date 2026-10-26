package io.github.dannygt97.holoprint.ui;

import io.github.dannygt97.holoprint.client.ControllerSupport;
import io.github.dannygt97.holoprint.config.HoloprintConfig;
import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.placement.Editor;
import io.github.dannygt97.holoprint.placement.EditorHud;
import io.github.dannygt97.holoprint.placement.EditorKeys;
import io.github.dannygt97.holoprint.placement.EditorMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The editor's tools on the hotbar: while the free camera is on, the nine slots show the toolbar's pictures instead of the
 * player's items. The number keys and the wheel pick a slot without taking the camera off the mouse. The first page has the
 * windows, the layer view and a folder, "Gr", that opens the second page: the editing modes (which take effect as soon as they
 * are picked) and Build; its last slot goes back. Every slot that is not an editing mode does something once and takes effect
 * with the use (or attack) click, and stays highlighted afterwards. The player's own selected slot is never touched.
 */
public final class HotbarTools {
	/** The tools of each page by the glyph they were registered with in the {@link Toolbar}; "Gr" opens the editing tools, "Bk" goes back, null is empty. */
	private static final String[][] PAGES = {
		{"Gr", "Ld", "Pl", "Ma", "Ly", "Ar", "Cf", null, null},
		{"M", "S", "C", "D", "Bu", null, null, null, "Bk"},
	};
	/** Tools that live in a window of the editor screen, so the screen has to be open for them. */
	private static final java.util.Set<String> WINDOWS = java.util.Set.of("Ma", "Ld", "Pl", "Ar", "Cf");
	private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
	private static final Identifier SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");
	/** How long the name of the tool that was just used stays above the bar. */
	private static final long NAME_MILLIS = 1800;

	private static String lastName = "";
	private static long lastUsed;
	private static int page;
	/** The slot that is highlighted. On the editing page, slots 0 to 3 follow the editing mode. */
	private static int slot;
	private static EditorMode lastMode = EditorMode.MOVE;
	/** After a click used a slot, the clicks of the same press (a held use button repeats) are thrown away. */
	private static boolean swallow;
	/** A new placement is in hand and has not been put down yet. */
	private static boolean inHand;

	private HotbarTools() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(HotbarTools::tick);
	}

	/** Where the player's own slot is, to notice when a controller moves it (see {@link #followController}). */
	private static int playerSlot = -1;

	/**
	 * A controller does not scroll a wheel or press the number keys: Controlify sets the player's slot directly (the bumpers go one slot
	 * on, the hotbar buttons jump to one). While the tools are on the hotbar and a controller is in use, such a change is taken for what it
	 * means here, the highlight moving along the tools, and the player's own slot goes back to where it was.
	 */
	private static void followController(Minecraft mc) {
		if (mc.player == null) {
			playerSlot = -1;
			return;
		}
		var inventory = mc.player.getInventory();
		int now = inventory.getSelectedSlot();
		if (enabled() && mc.gui.screen() == null && playerSlot >= 0 && now != playerSlot && ControllerSupport.active()) {
			inventory.setSelectedSlot(playerSlot);
			int step = Math.floorMod(now - playerSlot, 9);
			if (step == 1) {
				scroll(mc, -1.0);
			} else if (step == 8) {
				scroll(mc, 1.0);
			} else {
				pickSlot(mc, now);
			}
			return;
		}
		playerSlot = now;
	}

	private static void tick(Minecraft mc) {
		followController(mc);
		if (!enabled()) {
			page = 0;
			slot = 0;
			lastMode = Editor.mode();
			swallow = false;
			inHand = false;
			return;
		}
		// The mode was changed some other way (N, a button): the highlight follows it.
		if (Editor.mode() != lastMode) {
			lastMode = Editor.mode();
			if (page == 1) {
				slot = lastMode.ordinal();
			}
		}
		// A tool that was on and is off now (the placement it works on was finished): the highlight goes to the mode that is really on.
		if (page == 1 && glyph(slot) != null && !usable(slot)) {
			slot = Editor.mode().ordinal();
		}
		if (swallow && !mc.options.keyUse.isDown() && !mc.options.keyAttack.isDown()) {
			swallow = false;
		}
	}

	/** True while the hotbar shows the tools. */
	public static boolean enabled() {
		return Freecam.isActive() && HoloprintConfig.get().hotbarTools;
	}

	/**
	 * Something is now in the editor's hand (a schematic just loaded, a paste, a copy picked up): the highlight goes to the
	 * active editing mode, so that the next click reaches that tool and puts the thing down instead of repeating the last action.
	 */
	public static void focusTool() {
		inHand = false;
		if (!enabled()) {
			return;
		}
		page = 1;
		slot = Editor.mode().ordinal();
		lastMode = Editor.mode();
		announce(slot, false);
	}

	/** The highlight goes to Build (the key that changes the mode stopped there): a click builds. */
	public static void focusBuild() {
		inHand = false;
		if (!enabled() || !buildUsable()) {
			return;
		}
		page = 1;
		slot = 4;
		announce(slot, true);
	}

	/** A new placement (loaded, pasted, picked up to move) is in hand: like {@link #focusTool}, and the hotbar goes back to the main page once it is put down. */
	public static void holding() {
		focusTool();
		inHand = enabled();
	}

	/** What was in hand is put down (or let go): after putting it down the hotbar goes back to the main page, on the folder. */
	public static void released(boolean putDown) {
		boolean was = inHand;
		inHand = false;
		if (was && putDown && enabled()) {
			page = 0;
			slot = 0;
			lastMode = Editor.mode();
			// The same press may repeat; it must not open the folder it now lands on.
			swallow = true;
			announce(slot, false);
		}
	}

	/** The highlighted slot and the page shown (for the tests). */
	public static int slot() {
		return slot;
	}

	public static int page() {
		return page;
	}

	private static String glyph(int which) {
		return PAGES[page][which];
	}

	private static Toolbar.Tool tool(String glyph) {
		for (Toolbar.Tool tool : Toolbar.tools()) {
			if (tool.glyph().equals(glyph)) {
				return tool;
			}
		}
		return null;
	}

	private static Component name(String glyph, Toolbar.Tool tool) {
		if (tool != null) {
			return tool.name();
		}
		return "Gr".equals(glyph) ? Component.translatable("holoprint.tool.edit_group", Editor.mode().label()) : Component.translatable("holoprint.tool.back_tools");
	}

	/** Shows the name of a slot above the bar for a moment; an action says that it waits for a click. */
	private static void announce(int which, boolean waitsForClick) {
		String glyph = glyph(which);
		lastName = name(glyph, tool(glyph)).getString() + (waitsForClick ? " " + Component.translatable("holoprint.hotbar.click").getString() : "");
		lastUsed = System.currentTimeMillis();
	}

	/** True when the tool in the slot can be used right now (a folder, a window or an editing tool that has what it needs). */
	private static boolean usable(int which) {
		String glyph = glyph(which);
		if (glyph == null) {
			return false;
		}
		Toolbar.Tool tool = tool(glyph);
		return tool == null || tool.enabled().getAsBoolean();
	}

	/** True when Build can be used (there is a placement for it to build). */
	public static boolean buildUsable() {
		Toolbar.Tool tool = tool("Bu");
		return tool != null && tool.enabled().getAsBoolean();
	}

	/** Says why the tools that work on a placement are off, in yellow above the bar: there is none, or none is active (all are finished). */
	private static void blocked(Minecraft mc) {
		String key = HologramRenderer.all().isEmpty() ? "holoprint.hud.no_schematic" : "holoprint.hud.no_active";
		Component text = Component.translatable(key).append(" ").append(Component.translatable(key + ".2")).withStyle(net.minecraft.ChatFormatting.YELLOW);
		mc.gui.hud.setOverlayMessage(text, false);
	}

	private static boolean isMode(int which) {
		return page == 1 && which < 4;
	}

	/** Takes the number-key presses before the game uses them to change the player's slot. */
	public static void consumeKeys(Minecraft mc) {
		if (!enabled() || mc.gui.screen() != null) {
			return;
		}
		KeyMapping[] keys = mc.options.keyHotbarSlots;
		for (int key = 0; key < keys.length && key < 9; key++) {
			while (keys[key].consumeClick()) {
				pickSlot(mc, key);
			}
		}
	}

	/** Goes to the tool in a slot and uses it. A tool that is off does not take the highlight, and the reason is told. */
	private static void pickSlot(Minecraft mc, int which) {
		if (which < 0 || which >= 9 || glyph(which) == null) {
			return;
		}
		if (!usable(which)) {
			blocked(mc);
			return;
		}
		slot = which;
		use(mc, which);
	}

	/** The wheel moves the highlight along the slots (down = next, as with the hotbar), skipping the empty ones; an editing mode takes effect when it is reached. */
	public static void scroll(Minecraft mc, double wheel) {
		int step = wheel > 0 ? -1 : 1;
		for (int tried = 0; tried < 9; tried++) {
			slot = Math.floorMod(slot + step, 9);
			// the empty slots and the tools that are off are passed over
			if (glyph(slot) != null && usable(slot)) {
				break;
			}
		}
		String glyph = glyph(slot);
		Toolbar.Tool tool = tool(glyph);
		announce(slot, !isMode(slot));
		if (isMode(slot) && tool != null && tool.enabled().getAsBoolean()) {
			tool.action().run();
			// The highlight is already where the wheel put it; the tick must not take the mode change for an outside one and move it back.
			lastMode = Editor.mode();
		}
	}

	/** A use or attack click while an action slot is highlighted does that action instead of the mode's click; true when it was taken. */
	public static boolean click(Minecraft mc) {
		if (!enabled() || mc.gui.screen() != null) {
			return false;
		}
		if (swallow) {
			return true;
		}
		if (isMode(slot)) {
			return false;
		}
		use(mc, slot);
		swallow = true;
		return true;
	}

	/** Does what the tool in the slot does (the same as its button in the toolbar). */
	public static void use(Minecraft mc, int which) {
		String glyph = glyph(which);
		if (glyph == null) {
			return;
		}
		if ("Gr".equals(glyph) || "Bk".equals(glyph)) {
			boolean opening = "Gr".equals(glyph);
			page = opening ? 1 : 0;
			slot = opening ? Editor.mode().ordinal() : 0;
			announce(slot, false);
			return;
		}
		Toolbar.Tool tool = tool(glyph);
		announce(which, false);
		if (tool == null || !tool.enabled().getAsBoolean()) {
			blocked(mc);
			return;
		}
		if (WINDOWS.contains(glyph) && mc.gui.screen() == null) {
			EditorScreen.open(mc);
		}
		tool.action().run();
		if (isMode(which)) {
			lastMode = Editor.mode();
		}
	}

	/** Draws the bar in place of the game's hotbar; false when the game should draw its own. */
	public static boolean render(GuiGraphicsExtractor graphics) {
		if (!enabled()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		int left = graphics.guiWidth() / 2 - 91;
		int top = graphics.guiHeight() - 22;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, left, top, 182, 22);
		for (int which = 0; which < 9; which++) {
			String glyph = glyph(which);
			if (glyph == null) {
				continue;
			}
			Toolbar.Tool tool = tool(glyph);
			boolean on = tool != null && tool.selected().getAsBoolean();
			boolean usable = tool == null || tool.enabled().getAsBoolean();
			int color = !usable ? 0xFF707070 : on ? 0xFFFFD166 : 0xFFFFFFFF;
			if ("Gr".equals(glyph)) {
				// The folder shows the editing mode that is on, with a small arrow that says there is more inside.
				Icons.draw(graphics, Editor.mode().glyph(), left + 3 + which * 20, top + 3, 16, color);
				for (int[] dot : new int[][] {{0, 0}, {1, 1}, {2, 2}, {1, 3}, {0, 4}}) {
					graphics.fill(left + 16 + which * 20 + dot[0], top + 3 + dot[1], left + 17 + which * 20 + dot[0], top + 4 + dot[1], 0xFFA0A0A0);
				}
			} else {
				Icons.draw(graphics, glyph, left + 3 + which * 20, top + 3, 16, color);
			}
			String number = Integer.toString(which + 1);
			graphics.text(mc.font, number, left + 19 + which * 20 - mc.font.width(number), top + 11, 0xFFA0A0A0, true);
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, left - 1 + slot * 20, top - 1, 24, 23);
		java.util.List<Component> legend = legend(glyph(slot));
		long age = System.currentTimeMillis() - lastUsed;
		if (!legend.isEmpty()) {
			// What the highlighted tool does and which keys go with it, for as long as it is highlighted.
			Component title = "Bu".equals(glyph(slot)) ? Component.translatable("holoprint.hotbar.title.Bu") : name(glyph(slot), tool(glyph(slot)));
			int width = mc.font.width(title);
			for (Component line : legend) {
				width = Math.max(width, mc.font.width(line));
			}
			int height = (legend.size() + 1) * 11 + 4;
			int x = graphics.guiWidth() / 2 - width / 2;
			int y = top - 6 - height;
			graphics.fill(x - 5, y, x + width + 5, y + height, 0xAA000000);
			graphics.text(mc.font, title, x, y + 3, 0xFFFFD166, true);
			for (int i = 0; i < legend.size(); i++) {
				graphics.text(mc.font, legend.get(i), x, y + 3 + 11 * (i + 1), 0xFFFFFFFF, true);
			}
		} else if (age < NAME_MILLIS && !lastName.isEmpty()) {
			graphics.centeredText(mc.font, lastName, graphics.guiWidth() / 2, top - 14, 0xFFFFFFFF);
		}
		return true;
	}

	/** The explanation shown above the bar while a tool that needs one is highlighted; empty for the others. */
	private static java.util.List<Component> legend(String glyph) {
		if (glyph == null) {
			return java.util.List.of();
		}
		return switch (glyph) {
			case "Gr" -> java.util.List.of(EditorHud.line("holoprint.hotbar.legend.Gr.0"));
			case "Ly" -> java.util.List.of(EditorHud.line("holoprint.hotbar.legend.Ly.0"),
				EditorHud.line("holoprint.hotbar.legend.Ly.1", EditorHud.name(EditorKeys.LAYER_UP), EditorHud.name(EditorKeys.LAYER_DOWN), EditorHud.name(EditorKeys.LAYER_MODE)));
			case "Bu" -> java.util.List.of(EditorHud.line("holoprint.hotbar.legend.Bu.0"), EditorHud.line("holoprint.hotbar.legend.Bu.1"),
				EditorHud.line("holoprint.hotbar.legend.Bu.2", EditorHud.name(EditorKeys.BUILD)));
			default -> java.util.List.of();
		};
	}
}
