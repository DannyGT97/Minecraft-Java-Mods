package io.github.dannygt97.holoprint.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.isxander.controlify.InputMode;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.bind.InputBindingBuilder;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.bindings.BindContext;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.bindings.input.AxisInput;
import dev.isxander.controlify.bindings.input.ButtonInput;
import dev.isxander.controlify.bindings.input.Input;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.input.ControllerStateView;
import io.github.dannygt97.holoprint.placement.EditorKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Everything that talks to Controlify. Every action of the editor is a key mapping already; here each one also becomes a Controlify
 * binding that presses that key mapping when its button is pressed, so the controller and the keyboard share the code that does the
 * work. Controlify lists them under "Holoprint" in its controls (and so in its radial menu, where every binding can be put). Called only when Controlify is installed.
 */
final class ControlifyBridge {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("holoprint");
	private static final Map<KeyMapping, InputBindingSupplier> BINDINGS = new HashMap<>();

	private ControlifyBridge() {}

	/** True while a controller is connected and Controlify's input mode is not keyboard and mouse. */
	static boolean active() {
		try {
			ControlifyApi api = ControlifyApi.get();
			return api.getCurrentController().isPresent() && !api.currentInputMode().isKeyboardMouse();
		} catch (Throwable e) {
			return false;
		}
	}

	/** True while the controller is the input being used (not the mixed mode, where the keyboard works as well). */
	static boolean exclusive() {
		try {
			ControlifyApi api = ControlifyApi.get();
			return api.getCurrentController().isPresent() && api.currentInputMode() == InputMode.CONTROLLER;
		} catch (Throwable e) {
			return false;
		}
	}

	/** True while a controller is connected, whichever input the player is using. */
	static boolean connected() {
		try {
			return ControlifyApi.get().getCurrentController().isPresent();
		} catch (Throwable e) {
			return false;
		}
	}

	private static InputBinding binding(KeyMapping key) {
		InputBindingSupplier supplier = BINDINGS.get(key);
		ControllerEntity controller = ControlifyApi.get().getCurrentController().orElse(null);
		return supplier == null || controller == null ? null : supplier.onOrNull(controller);
	}

	/** True when the controller button of {@code key} is the one Holoprint gives it by default. */
	static boolean isDefault(KeyMapping key) {
		try {
			InputBinding binding = binding(key);
			return binding == null || binding.boundInput().equals(binding.defaultInput());
		} catch (Throwable e) {
			return true;
		}
	}

	static void reset(KeyMapping key) {
		try {
			InputBinding binding = binding(key);
			if (binding != null) {
				binding.setBoundInput(binding.defaultInput());
			}
		} catch (Throwable e) {
			LOGGER.warn("Holoprint could not reset a controller binding", e);
		}
	}

	/** What is held on the controller when a button is being waited for: those are not taken for the new button. */
	private static Set<Input> held = Set.of();

	private static Set<Input> down() {
		Set<Input> out = new HashSet<>();
		ControllerEntity controller = ControlifyApi.get().getCurrentController().orElse(null);
		if (controller == null || controller.input().isEmpty()) {
			return out;
		}
		ControllerStateView state = controller.input().get().stateNow();
		for (Identifier id : state.getButtons()) {
			if (state.isButtonDown(id)) {
				out.add(new ButtonInput(id));
			}
		}
		for (Identifier id : state.getAxes()) {
			if (Math.abs(state.getAxisState(id)) > 0.8F) {
				out.add(new AxisInput(id));
			}
		}
		return out;
	}

	static void beginCapture() {
		try {
			held = down();
		} catch (Throwable e) {
			held = Set.of();
		}
	}

	/** Gives {@code key} the first button that goes down on the controller (not one that was already down); true when it did. */
	static boolean capture(KeyMapping key) {
		try {
			Set<Input> now = down();
			Input pressed = null;
			for (Input input : now) {
				if (!held.contains(input)) {
					pressed = input;
					break;
				}
			}
			held = now;
			InputBinding binding = pressed == null ? null : binding(key);
			if (binding == null) {
				return false;
			}
			binding.setBoundInput(pressed);
			return true;
		} catch (Throwable e) {
			LOGGER.warn("Holoprint could not read the controller", e);
			return false;
		}
	}

	static Component glyph(KeyMapping key) {
		try {
			InputBindingSupplier supplier = BINDINGS.get(key);
			ControllerEntity controller = ControlifyApi.get().getCurrentController().orElse(null);
			if (supplier == null || controller == null) {
				return null;
			}
			InputBinding binding = supplier.onOrNull(controller);
			return binding == null || binding.isUnbound() ? null : binding.inputGlyph();
		} catch (Throwable e) {
			return null;
		}
	}

	/** The glyph of one of Controlify's own bindings (the ones of the game itself), or null when it has no button. */
	static Component gameGlyph(String name) {
		try {
			InputBindingSupplier supplier = switch (name) {
				case "attack" -> ControlifyBindings.ATTACK;
				case "use" -> ControlifyBindings.USE;
				case "jump" -> ControlifyBindings.JUMP;
				case "sneak" -> ControlifyBindings.SNEAK;
				case "next_slot" -> ControlifyBindings.NEXT_SLOT;
				case "prev_slot" -> ControlifyBindings.PREV_SLOT;
				default -> null;
			};
			ControllerEntity controller = ControlifyApi.get().getCurrentController().orElse(null);
			if (supplier == null || controller == null) {
				return null;
			}
			InputBinding binding = supplier.onOrNull(controller);
			return binding == null || binding.isUnbound() ? null : binding.inputGlyph();
		} catch (Throwable e) {
			return null;
		}
	}

	/** The id every binding is registered under is {@code holoprint:<key mapping name without its prefix>}. */
	static void register(ControlifyBindApi api) {
		Component category = Component.translatable("holoprint.controlify.category");
		// The buttons are the ones the game does not use while the free camera is on: B has no job in the game itself, and the pick-block
		// button (D-pad left) is taken over by the editor while the camera flies.
		add(api, category, EditorKeys.PAD_BACK, "pad_back", "east");
		add(api, category, EditorKeys.ROTATE, "rotate", "dpad_left");
		add(api, category, EditorKeys.EDITOR, "editor", null);
		add(api, category, EditorKeys.MODE, "mode", null);
		add(api, category, EditorKeys.INTERFACE, "interface", null);
		add(api, category, EditorKeys.CANCEL, "cancel", null);
		add(api, category, EditorKeys.MIRROR, "mirror", null);
		add(api, category, EditorKeys.FORWARD, "nudge_forward", null);
		add(api, category, EditorKeys.BACK, "nudge_back", null);
		add(api, category, EditorKeys.LEFT, "nudge_left", null);
		add(api, category, EditorKeys.RIGHT, "nudge_right", null);
		add(api, category, EditorKeys.UP, "nudge_up", null);
		add(api, category, EditorKeys.DOWN, "nudge_down", null);
		add(api, category, EditorKeys.SPEED_DOWN, "speed_down", null);
		add(api, category, EditorKeys.SPEED_UP, "speed_up", null);
		add(api, category, EditorKeys.COPY, "copy", null);
		add(api, category, EditorKeys.CUT, "cut", null);
		add(api, category, EditorKeys.PASTE, "paste", null);
		add(api, category, EditorKeys.DELETE, "delete", null);
		add(api, category, EditorKeys.SAVE, "save", null);
		add(api, category, EditorKeys.BUILD, "build", null);
		add(api, category, EditorKeys.MATERIALS, "materials", null);
		add(api, category, EditorKeys.PIN_MATERIALS, "pin_materials", null);
		add(api, category, EditorKeys.LAYER_UP, "layer_up", null);
		add(api, category, EditorKeys.LAYER_DOWN, "layer_down", null);
		add(api, category, EditorKeys.LAYER_MODE, "layer_mode", null);
		add(api, category, EditorKeys.EASY_PLACE, "easy_place", null);
		add(api, category, EditorKeys.AUTO_FILL, "auto_fill", null);
	}

	private static void add(ControlifyBindApi api, Component category, KeyMapping key, String name, String defaultButton) {
		InputBindingSupplier supplier = api.registerBinding(builder -> {
			InputBindingBuilder out = builder.id("holoprint", name)
				.category(category)
				.name(Component.translatable("key.holoprint." + name))
				.description(Component.translatable("holoprint.controlify.desc." + name))
				.allowedContexts(BindContext.IN_GAME)
				.keyEmulation(key);
			if (defaultButton != null) {
				out = out.defaultInput(new ButtonInput(Identifier.fromNamespaceAndPath("controlify", "button/" + defaultButton)));
			}
			return out;
		});
		BINDINGS.put(key, supplier);
	}
}
