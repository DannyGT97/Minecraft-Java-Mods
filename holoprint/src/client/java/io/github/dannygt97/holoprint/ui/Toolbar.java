package io.github.dannygt97.holoprint.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.network.chat.Component;

/**
 * The column of buttons at the right of the editor. It is a plain list: anything that wants a
 * button (an editing mode, an action, a window) registers a {@link Tool}, so the bar can grow
 * without being rebuilt.
 */
public final class Toolbar {
	/**
	 * One button. {@code group} separates the bar into blocks (modes, actions, windows) with a small
	 * gap; {@code glyph} is the short text drawn on it until icons exist; {@code selected} marks the
	 * active editing mode and {@code enabled} greys it out when it cannot be used right now.
	 */
	public record Tool(int group, String glyph, Component name, Runnable action, BooleanSupplier selected, BooleanSupplier enabled) {}

	private static final List<Tool> TOOLS = new ArrayList<>();

	private Toolbar() {}

	public static void register(Tool tool) {
		TOOLS.add(tool);
	}

	public static List<Tool> tools() {
		return TOOLS;
	}
}
