package io.github.dannygt97.holoprint.placement;

import java.util.List;

import io.github.dannygt97.holoprint.client.ControllerSupport;
import io.github.dannygt97.holoprint.config.HoloprintConfig;
import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The key legend shown in a corner of the screen while the editor is open. */
public final class EditorHud {
	private static final int MARGIN = 6;
	private static final int LINE = 11;
	private static final float MAX_SCALE = 0.8F;
	private static final int PANEL_SHADE = 0x88000000;

	private EditorHud() {}

	/** The name of a key as the legends show it: green, so the keys stand out from the text (the lang files colour mouse words blue). */
	/** The name of {@code key} with Ctrl before it, all in the same colour. */
	public static Component ctrl(KeyMapping key) {
		// a controller has no Ctrl: its button for the action is enough
		Component glyph = ControllerSupport.glyph(key);
		if (glyph != null) {
			return glyph.copy();
		}
		return Component.literal("Ctrl+").append(key.getTranslatedKeyMessage()).withStyle(ChatFormatting.GREEN);
	}

	/** With a controller in use and a button set for the action, the legends show that button's glyph instead of the key. */
	public static Component name(KeyMapping key) {
		Component glyph = ControllerSupport.glyph(key);
		if (glyph != null) {
			return glyph.copy();
		}
		return key.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GREEN);
	}

	private static final java.util.regex.Pattern CLICK = java.util.regex.Pattern.compile("§b(left click|right click|click|clic izquierdo|clic derecho|clic)§r", java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern WHEEL = java.util.regex.Pattern.compile("§b(mouse wheel|wheel|rueda)§r", java.util.regex.Pattern.CASE_INSENSITIVE);

	/**
	 * A line of a legend. With a controller in use, the mouse words in it (click, right click, wheel) become the controller's buttons for them, the way
	 * the keys already do: the text is the translation with those words swapped for arguments, and is shown through a fallback key nobody defines.
	 */
	public static Component line(String key, Object... args) {
		if (!ControllerSupport.legendsOnPad()) {
			return Component.translatable(key, args);
		}
		String raw = net.minecraft.locale.Language.getInstance().getOrDefault(key);
		List<Object> all = new java.util.ArrayList<>(List.of(args));
		java.util.regex.Matcher matcher = CLICK.matcher(raw);
		StringBuilder text = new StringBuilder();
		while (matcher.find()) {
			String word = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
			all.add(ControllerSupport.gameGlyph(word.contains("right") || word.contains("derecho") ? "use" : "attack").copy());
			matcher.appendReplacement(text, java.util.regex.Matcher.quoteReplacement("%" + all.size() + "$s"));
		}
		matcher.appendTail(text);
		matcher = WHEEL.matcher(text.toString());
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			all.add(ControllerSupport.gameGlyph("prev_slot").copy().append(" / ").append(ControllerSupport.gameGlyph("next_slot")));
			matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement("%" + all.size() + "$s"));
		}
		matcher.appendTail(out);
		return Component.translatableWithFallback("holoprint.hud.generated", out.toString(), all.toArray());
	}

	public static Component hotbar() {
		if (!ControllerSupport.legendsOnPad()) {
			return Component.translatable("holoprint.hud.hotbar");
		}
		return Component.translatable("holoprint.hud.hotbar.pad", ControllerSupport.gameGlyph("prev_slot"), ControllerSupport.gameGlyph("next_slot"));
	}

	/** How to fly: with a controller, the left stick moves, and the buttons of jump and sneak go up and down. */
	public static Component fly(boolean tools, String speed) {
		if (!ControllerSupport.legendsOnPad()) {
			return Component.translatable(tools ? "holoprint.hud.fly_tools" : "holoprint.hud.fly", speed);
		}
		return Component.translatable(tools ? "holoprint.hud.fly_tools.pad" : "holoprint.hud.fly.pad", ControllerSupport.gameGlyph("jump"), ControllerSupport.gameGlyph("sneak"),
			ControllerSupport.gameGlyph("prev_slot"), ControllerSupport.gameGlyph("next_slot"), name(EditorKeys.SPEED_DOWN), name(EditorKeys.SPEED_UP), speed);
	}

	static void render(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (!Editor.isActive() || !HoloprintConfig.get().showLegend) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		// The legend would show through a window, so it is hidden while one is open.
		if (mc.gui.screen() instanceof io.github.dannygt97.holoprint.ui.EditorScreen editor && editor.hasPanel()) {
			return;
		}
		List<Component> lines = new java.util.ArrayList<>();
		lines.add(Component.translatable("holoprint.hud.title", Editor.mode().label()));
		var active = HologramRenderer.active();
		if (active != null) {
			int index = HologramRenderer.all().indexOf(active);
			lines.add(Component.translatable("holoprint.hud.placement", io.github.dannygt97.holoprint.ui.panel.PlacementsPanel.nameOf(active, Math.max(0, index)), index + 1, HologramRenderer.all().size()).withStyle(ChatFormatting.AQUA));
		}
		var clipboard = io.github.dannygt97.holoprint.selection.Clipboard.schematic();
		if (clipboard != null) {
			String name = clipboard.name() == null || clipboard.name().isBlank() ? "?" : clipboard.name();
			lines.add(Component.translatable("holoprint.hud.clipboard", name, clipboard.totalBlocks()).withStyle(ChatFormatting.GRAY));
		}
		lines.addAll(Editor.legend());
		lines.add(Component.translatable("holoprint.hud.mode", name(EditorKeys.MODE)));
		lines.add(Component.translatable("holoprint.hud.leave", name(EditorKeys.EDITOR)));
		lines.add(io.github.dannygt97.holoprint.ui.HotbarTools.enabled() ? hotbar() : Component.translatable("holoprint.hud.interface", name(EditorKeys.INTERFACE)));
		lines.add(Component.translatable("holoprint.hud.layers", LayerControl.modeName(), name(EditorKeys.LAYER_MODE), name(EditorKeys.LAYER_UP), name(EditorKeys.LAYER_DOWN)));
		lines.add(Component.translatable("holoprint.hud.materials", name(EditorKeys.MATERIALS)));
		lines.add(fly(io.github.dannygt97.holoprint.ui.HotbarTools.enabled(), String.format("%.1f", Freecam.speed())));
		if (HologramRenderer.all().isEmpty()) {
			lines.add(Component.translatable("holoprint.hud.no_schematic").withStyle(ChatFormatting.YELLOW));
			lines.add(Component.translatable("holoprint.hud.no_schematic.2").withStyle(ChatFormatting.YELLOW));
		} else if (active == null) {
			// the tools that work on a placement (Move, Build) are off, and why
			lines.add(Component.translatable("holoprint.hud.no_active").withStyle(ChatFormatting.YELLOW));
			lines.add(Component.translatable("holoprint.hud.no_active.2").withStyle(ChatFormatting.YELLOW));
		} else if (HologramRenderer.isTrimmed()) {
			lines.add(Component.translatable("holoprint.hud.trimmed", HologramRenderer.lastDrawn, HologramRenderer.lastInView).withStyle(ChatFormatting.YELLOW));
		}
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, mc.font.width(line));
		}
		int height = lines.size() * LINE + 6;
		// The legend is drawn smaller the more of the screen it would take (a big GUI scale makes it huge).
		float scale = Math.max(0.5F, Math.min(MAX_SCALE, Math.min(0.40F * graphics.guiWidth() / (width + 10), 0.30F * (graphics.guiHeight()) / height)));
		graphics.pose().pushMatrix();
		graphics.pose().translate(MARGIN, MARGIN);
		graphics.pose().scale(scale, scale);
		graphics.fill(0, 0, width + 10, height, PANEL_SHADE);
		for (int i = 0; i < lines.size(); i++) {
			int color = i == 0 ? 0xFFFFD166 : 0xFFFFFFFF;
			graphics.text(mc.font, lines.get(i), 5, 4 + i * LINE, color, true);
		}
		graphics.pose().popMatrix();
	}
}
