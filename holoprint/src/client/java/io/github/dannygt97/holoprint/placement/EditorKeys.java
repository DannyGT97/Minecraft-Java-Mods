package io.github.dannygt97.holoprint.placement;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Every key of the editor. They are ordinary key mappings, so they show up in the game's controls
 * screen and a controller mod can map each one to a button.
 */
public final class EditorKeys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("holoprint", "main"));

	/** Opens the editor (the free camera) and closes it again. */
	public static final KeyMapping EDITOR = key("editor", InputConstants.KEY_G);
	/** Switches to the next editing mode. */
	public static final KeyMapping MODE = key("mode", InputConstants.KEY_N);
	/** Switches between turning the camera with the mouse and a free cursor for the buttons. */
	public static final KeyMapping INTERFACE = key("interface", InputConstants.KEY_TAB);
	/** Flying speed of the free camera, down and up (the wheel does it too, unless it is picking a tool on the hotbar). */
	public static final KeyMapping SPEED_DOWN = key("speed_down", InputConstants.KEY_COMMA);
	public static final KeyMapping SPEED_UP = key("speed_up", InputConstants.KEY_PERIOD);
	public static final KeyMapping ROTATE = key("rotate", InputConstants.KEY_R);
	public static final KeyMapping MIRROR = key("mirror", InputConstants.KEY_M);
	public static final KeyMapping CANCEL = key("cancel", InputConstants.KEY_BACKSPACE);
	public static final KeyMapping FORWARD = key("nudge_forward", InputConstants.KEY_UP);
	public static final KeyMapping BACK = key("nudge_back", InputConstants.KEY_DOWN);
	public static final KeyMapping LEFT = key("nudge_left", InputConstants.KEY_LEFT);
	public static final KeyMapping RIGHT = key("nudge_right", InputConstants.KEY_RIGHT);
	public static final KeyMapping UP = key("nudge_up", InputConstants.KEY_PAGEUP);
	public static final KeyMapping DOWN = key("nudge_down", InputConstants.KEY_PAGEDOWN);
	/** With Ctrl held: copy, cut and paste, like everywhere else. */
	public static final KeyMapping COPY = key("copy", InputConstants.KEY_C);
	public static final KeyMapping CUT = key("cut", InputConstants.KEY_X);
	public static final KeyMapping PASTE = key("paste", InputConstants.KEY_V);
	public static final KeyMapping DELETE = key("delete", InputConstants.KEY_DELETE);
	public static final KeyMapping SAVE = key("save", InputConstants.KEY_K);
	/** Builds the whole active hologram in the world (needs cheats). */
	public static final KeyMapping BUILD = key("build", InputConstants.KEY_B);
	/** Turns the placement of the blocks a hologram asks for on and off (unbound until the player picks a key). */
	public static final KeyMapping EASY_PLACE = key("easy_place", InputConstants.UNKNOWN.getValue());
	/** Turns the auto-fill (keep placing while the use button is held) on and off (unbound until the player picks a key). */
	public static final KeyMapping AUTO_FILL = key("auto_fill", InputConstants.UNKNOWN.getValue());
	/** Layer view: raise or lower the layer that is shown, and switch between all layers and a single one. */
	public static final KeyMapping LAYER_UP = key("layer_up", InputConstants.KEY_ADD);
	public static final KeyMapping LAYER_DOWN = key("layer_down", 86 /* numeric keypad minus: InputConstants has no name for it */);
	/** Opens the material list of the active placement, and closes it again. */
	public static final KeyMapping MATERIALS = key("materials", InputConstants.KEY_J);
	public static final KeyMapping LAYER_MODE = key("layer_mode", InputConstants.KEY_MULTIPLY);

	private EditorKeys() {}

	private static KeyMapping key(String name, int code) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.holoprint." + name, code, CATEGORY));
	}

	/** Loads the class so the key mappings get registered. */
	public static void init() {}

	/** Throws away presses nobody used, so they do not pile up and fire later in another mode. */
	static void drainUnused() {
		for (KeyMapping key : new KeyMapping[] {SPEED_DOWN, SPEED_UP, ROTATE, MIRROR, FORWARD, BACK, LEFT, RIGHT, UP, DOWN, COPY, CUT, PASTE, DELETE, SAVE, BUILD, MATERIALS}) {
			while (key.consumeClick()) {
				// discarded
			}
		}
	}
}
