package io.github.dannygt97.holoprint.placement;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** What the editor does in one mode. The editor calls the tool that matches the current mode. */
interface EditorTool {
	/** The mode becomes current. {@code fresh} is true when the editor has just opened. */
	void enter(Minecraft mc, boolean fresh);

	/** Another mode is about to become current: leave things as they are, nothing is lost. */
	void leave(Minecraft mc);

	/** Every tick while this mode is current. {@code ray} is null while the pointer is over the interface. */
	void tick(Minecraft mc, Vec3 ray);

	void primaryClick(Minecraft mc, Vec3 ray);

	void secondaryClick(Minecraft mc, Vec3 ray);

	/** The cancel key. */
	void cancel(Minecraft mc);

	/**
	 * Escape: cancels what is being done (a move not yet confirmed, a selection being made, a copy being carried) and returns true, or returns false when there
	 * is nothing to cancel, so that Escape does what it always does (the pause menu).
	 */
	default boolean escape(Minecraft mc) {
		return false;
	}

	/** Help lines for the corner of the screen, after the title. */
	List<Component> legend();
}
