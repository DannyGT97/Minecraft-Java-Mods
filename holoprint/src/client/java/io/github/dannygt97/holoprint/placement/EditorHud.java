package io.github.dannygt97.holoprint.placement;

import java.util.List;

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

	static Component name(KeyMapping key) {
		return key.getTranslatedKeyMessage();
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
		lines.add(Component.translatable("holoprint.hud.interface", name(EditorKeys.INTERFACE)));
		lines.add(Component.translatable("holoprint.hud.layers", LayerControl.modeName(), name(EditorKeys.LAYER_MODE), name(EditorKeys.LAYER_UP), name(EditorKeys.LAYER_DOWN)));
		lines.add(Component.translatable("holoprint.hud.materials", name(EditorKeys.MATERIALS)));
		lines.add(Component.translatable(io.github.dannygt97.holoprint.ui.HotbarTools.enabled() ? "holoprint.hud.fly_tools" : "holoprint.hud.fly", String.format("%.1f", Freecam.speed())));
		if (HologramRenderer.all().isEmpty()) {
			lines.add(Component.translatable("holoprint.hud.no_schematic").withStyle(ChatFormatting.YELLOW));
		} else if (HologramRenderer.isTrimmed()) {
			lines.add(Component.translatable("holoprint.hud.trimmed", HologramRenderer.lastDrawn, HologramRenderer.lastInView).withStyle(ChatFormatting.YELLOW));
		}
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, mc.font.width(line));
		}
		int height = lines.size() * LINE + 6;
		// The legend is drawn smaller the more of the screen it would take (a big GUI scale makes it huge).
		float scale = Math.max(0.5F, Math.min(MAX_SCALE, Math.min(0.40F * graphics.guiWidth() / (width + 10), 0.30F * graphics.guiHeight() / height)));
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
