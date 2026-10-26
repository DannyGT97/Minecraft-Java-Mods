package io.github.dannygt97.holoprint.client;

import io.github.dannygt97.holoprint.placement.EditorKeys;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * What Holoprint does differently while a controller is in use through Controlify. Controlify is optional: without it, or while the
 * player uses the keyboard and mouse, {@link #active()} is false and nothing here changes anything. The only class that touches
 * Controlify's classes is {@link ControlifyBridge}, and it is never loaded when the mod is not installed.
 */
public final class ControllerSupport {
	private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("controlify");

	/** Pretends a controller is in use (for the dev tests, which have no controller). */
	public static volatile boolean forced;

	private ControllerSupport() {}

	/** True while a controller is connected and the player is using it (Controlify's input mode is not keyboard and mouse). */
	public static boolean active() {
		return forced || INSTALLED && ControlifyBridge.active();
	}

	/** True while a controller is connected (Controlify installed), whichever input the player is using: the settings then show a button column for it. */
	public static boolean connected() {
		return INSTALLED && ControlifyBridge.connected();
	}

	/** The controller button of {@code key} for the settings window: its glyph, or "-" when it has none. */
	public static Component padLabel(KeyMapping key) {
		Component glyph = INSTALLED ? ControlifyBridge.glyph(key) : null;
		return glyph != null ? glyph : Component.literal("-");
	}

	public static boolean padIsDefault(KeyMapping key) {
		return !INSTALLED || ControlifyBridge.isDefault(key);
	}

	public static void padReset(KeyMapping key) {
		if (INSTALLED) {
			ControlifyBridge.reset(key);
		}
	}

	/** Starts waiting for a button of the controller: what is held now is not taken for it. */
	public static void padBeginListening() {
		if (INSTALLED) {
			ControlifyBridge.beginCapture();
		}
	}

	/** While waiting: gives {@code key} the first button that goes down on the controller; true once it has. */
	public static boolean padListen(KeyMapping key) {
		return INSTALLED && ControlifyBridge.capture(key);
	}

	/**
	 * How a legend names the controller button that does what {@code key} does: its glyph, or "no button" when none is set for it (the player
	 * sets them in Controlify's controls). Null when the controller is not the only input in use: the legend then shows the key.
	 */
	public static Component glyph(KeyMapping key) {
		if (!INSTALLED || !(forced || ControlifyBridge.exclusive())) {
			return null;
		}
		Component glyph = ControlifyBridge.glyph(key);
		// Cancelling on a controller is the same button that opens and closes the editor.
		if (glyph == null && key == EditorKeys.CANCEL) {
			glyph = ControlifyBridge.glyph(EditorKeys.PAD_BACK);
		}
		return glyph != null ? glyph : Component.translatable("holoprint.controlify.unbound").withStyle(ChatFormatting.GRAY);
	}

	/** True while the legends should name controller buttons: the controller is the only input in use. */
	public static boolean legendsOnPad() {
		return INSTALLED && (forced || ControlifyBridge.exclusive());
	}

	/**
	 * The glyph of one of the game's own controller buttons ("attack", "use", "jump", "sneak", "next_slot", "prev_slot") for a legend, or "no button"
	 * when none is set. Null when the legends name keys.
	 */
	public static Component gameGlyph(String name) {
		if (!legendsOnPad()) {
			return null;
		}
		Component glyph = ControlifyBridge.gameGlyph(name);
		return glyph != null ? glyph : Component.translatable("holoprint.controlify.unbound").withStyle(ChatFormatting.GRAY);
	}
}
