package io.github.dannygt97.holoprint.ui;

import io.github.dannygt97.holoprint.config.HoloprintConfig;
import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.placement.Editor;
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
 * player's items. The number keys and the wheel pick a slot without taking the camera off the mouse: slots 1 to 4 are the editing
 * modes and take effect at once; the others do something once and take effect with the use (or attack) click. The player's own
 * selected slot is never touched. {@link EditorScreen} (Tab) still has the whole toolbar and the windows.
 */
public final class HotbarTools {
	/** The tools in the nine slots, by the glyph they were registered with in the {@link Toolbar}; "Mn" opens the whole editor screen. */
	private static final String[] SLOTS = {"M", "S", "C", "D", "Ly", "Ma", "Ld", "Bu", "Mn"};
	/** Tools that live in a window of the editor screen, so the screen has to be open for them. */
	private static final java.util.Set<String> WINDOWS = java.util.Set.of("Ma", "Ld");
	private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
	private static final Identifier SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");
	/** How long the name of the tool that was just used stays above the bar. */
	private static final long NAME_MILLIS = 1800;

	private static String lastName = "";
	private static long lastUsed;
	/** The slot that is highlighted. Slots 0 to 3 follow the editing mode; the others are actions that wait for a click. */
	private static int slot;
	private static EditorMode lastMode = EditorMode.MOVE;
	/** After a click used a slot, the clicks of the same press (a held use button repeats) are thrown away. */
	private static boolean swallow;

	private HotbarTools() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(HotbarTools::tick);
	}

	private static void tick(Minecraft mc) {
		if (!enabled()) {
			slot = Editor.mode().ordinal();
			lastMode = Editor.mode();
			swallow = false;
			return;
		}
		// The mode was changed some other way (N, a button): the highlight follows it.
		if (Editor.mode() != lastMode) {
			lastMode = Editor.mode();
			slot = lastMode.ordinal();
		}
		if (swallow && !mc.options.keyUse.isDown() && !mc.options.keyAttack.isDown()) {
			swallow = false;
		}
	}

	/** True while the hotbar shows the tools. */
	public static boolean enabled() {
		return Freecam.isActive() && HoloprintConfig.get().hotbarTools;
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
		return tool != null ? tool.name() : Component.translatable("holoprint.tool.menu");
	}

	/** Takes the number-key presses before the game uses them to change the player's slot. */
	public static void consumeKeys(Minecraft mc) {
		if (!enabled() || mc.gui.screen() != null) {
			return;
		}
		KeyMapping[] keys = mc.options.keyHotbarSlots;
		for (int key = 0; key < keys.length && key < SLOTS.length; key++) {
			while (keys[key].consumeClick()) {
				use(mc, key);
			}
		}
	}

	/** The wheel moves the highlight along the slots (down = next, as with the hotbar); an editing mode takes effect when it is reached. */
	public static void scroll(Minecraft mc, double wheel) {
		int step = wheel > 0 ? -1 : 1;
		slot = Math.floorMod(slot + step, SLOTS.length);
		Toolbar.Tool tool = tool(SLOTS[slot]);
		lastName = name(SLOTS[slot], tool).getString() + (slot >= 4 ? " " + Component.translatable("holoprint.hotbar.click").getString() : "");
		lastUsed = System.currentTimeMillis();
		if (slot < 4 && tool != null && tool.enabled().getAsBoolean()) {
			tool.action().run();
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
		if (slot < 4) {
			return false;
		}
		use(mc, slot);
		slot = Editor.mode().ordinal();
		swallow = true;
		return true;
	}

	/** Does what the tool in the slot does (the same as its button in the toolbar). */
	public static void use(Minecraft mc, int which) {
		String glyph = SLOTS[which];
		Toolbar.Tool tool = tool(glyph);
		lastName = name(glyph, tool).getString();
		lastUsed = System.currentTimeMillis();
		if (tool == null) {
			EditorScreen.open(mc);
			return;
		}
		if (!tool.enabled().getAsBoolean()) {
			return;
		}
		if (WINDOWS.contains(glyph) && mc.gui.screen() == null) {
			EditorScreen.open(mc);
		}
		tool.action().run();
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
		for (int slot = 0; slot < SLOTS.length; slot++) {
			Toolbar.Tool tool = tool(SLOTS[slot]);
			boolean on = tool != null && tool.selected().getAsBoolean();
			boolean usable = tool == null || tool.enabled().getAsBoolean();
			int color = !usable ? 0xFF707070 : on ? 0xFFFFD166 : 0xFFFFFFFF;
			Icons.draw(graphics, SLOTS[slot], left + 3 + slot * 20, top + 3, 16, color);
			String number = Integer.toString(slot + 1);
			graphics.text(mc.font, number, left + 19 + slot * 20 - mc.font.width(number), top + 11, 0xFFA0A0A0, true);
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, left - 1 + slot * 20, top - 1, 24, 23);
		long age = System.currentTimeMillis() - lastUsed;
		if (age < NAME_MILLIS && !lastName.isEmpty()) {
			graphics.centeredText(mc.font, lastName, graphics.guiWidth() / 2, top - 14, 0xFFFFFFFF);
		}
		return true;
	}
}
