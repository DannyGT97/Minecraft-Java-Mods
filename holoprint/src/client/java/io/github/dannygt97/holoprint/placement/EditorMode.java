package io.github.dannygt97.holoprint.placement;

import net.minecraft.network.chat.Component;

/** What a click does in the editor. */
public enum EditorMode {
	MOVE("M", "holoprint.mode.move"),
	SELECT("S", "holoprint.mode.select"),
	COPY("C", "holoprint.mode.copy"),
	DELETE("D", "holoprint.mode.delete");

	private final String glyph;
	private final String key;

	EditorMode(String glyph, String key) {
		this.glyph = glyph;
		this.key = key;
	}

	public String glyph() {
		return glyph;
	}

	public Component label() {
		return Component.translatable(key);
	}

	public EditorMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
