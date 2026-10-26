package io.github.dannygt97.holoprint.placement;

import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.hologram.LayerView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The keys that change which layers of the holograms are shown, and the note on the screen that says which one it is. */
public final class LayerControl {
	/** How long the "Layer X / Y" note stays on the screen after the layer or the mode changed. */
	private static final long NOTE_MILLIS = 3000;
	private static long noteUntil;

	private LayerControl() {}

	/** Runs every tick, in the editor and out of it. */
	static void tick(Minecraft mc) {
		while (EditorKeys.LAYER_MODE.consumeClick()) {
			toggleMode(mc);
		}
		while (EditorKeys.LAYER_UP.consumeClick()) {
			step(mc, 1);
		}
		while (EditorKeys.LAYER_DOWN.consumeClick()) {
			step(mc, -1);
		}
	}

	/** Switches between all layers and a single one (the key, or the toolbar button). */
	public static void toggleMode(Minecraft mc) {
		LayerView.toggleMode(mc);
		noteUntil = System.currentTimeMillis() + NOTE_MILLIS;
		if (mc.player != null) {
			mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.layer_mode", modeName()), false);
		}
	}

	/** Raises (positive) or lowers (negative) the layer shown on its own (the keys, or the toolbar buttons). */
	public static void step(Minecraft mc, int delta) {
		if (mc.player == null) {
			return;
		}
		if (LayerView.move(delta)) {
			noteUntil = System.currentTimeMillis() + NOTE_MILLIS;
			mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.layer", LayerView.layer() + 1, LayerView.height()), false);
		} else {
			mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.layer_all_only", EditorHud.name(EditorKeys.LAYER_MODE)), false);
		}
	}

	static Component modeName() {
		return Component.translatable(LayerView.mode() == LayerView.Mode.ALL ? "holoprint.layer.all" : "holoprint.layer.single");
	}

	/** A few seconds after the layer changed, says which one it is at the top of the screen (it does not stay there). */
	static void render(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (LayerView.mode() != LayerView.Mode.SINGLE || HologramRenderer.all().isEmpty() || System.currentTimeMillis() > noteUntil) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Component text = Component.translatable("holoprint.hud.layer", LayerView.layer() + 1, LayerView.height());
		int width = mc.font.width(text);
		int x = (graphics.guiWidth() - width) / 2;
		int y = 22;
		graphics.fill(x - 4, y - 3, x + width + 4, y + 11, 0x88000000);
		graphics.text(mc.font, text, x, y, 0xFFFFD166, true);
	}
}
