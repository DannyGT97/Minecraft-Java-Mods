package io.github.dannygt97.holoprint.ui;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * Short notes that tell the user what just happened or why something did not: information (blue), a warning (yellow) or an error (red).
 * Any code can raise one with {@link #info}, {@link #warning} or {@link #error}; they appear at the top of the screen, stack up, and fade out by themselves.
 * Showing the same text again restarts the one already on screen instead of piling up copies of it.
 * They are drawn by {@link #render}, from the HUD and from the editor's window, so they show wherever Holoprint has the screen.
 */
public final class Notices {
	public enum Kind {
		INFO("i", 0xFF4D9BFF),
		WARNING("!", 0xFFF2C94C),
		ERROR("x", 0xFFFF5555);

		private final String mark;
		private final int color;

		Kind(String mark, int color) {
			this.mark = mark;
			this.color = color;
		}
	}

	private static final long LIFE = 5000L;
	private static final long FADE_IN = 150L;
	private static final long FADE_OUT = 700L;
	private static final int MAX = 4;
	private static final int TOP = 10;
	private static final int GAP = 4;
	private static final int LINE = 10;
	private static final int PAD = 5;
	private static final int ICON = 12;
	private static final int MAX_WIDTH = 280;

	private static final List<Notice> ACTIVE = new ArrayList<>();

	private record Notice(Kind kind, Component text, long at) {}

	private Notices() {}

	/** Puts the notes on the HUD; the editor's window draws them itself, on top of its own panels, so the HUD leaves them to it. */
	public static void init() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("holoprint", "notices"), (graphics, tracker) -> {
			if (!(Minecraft.getInstance().gui.screen() instanceof EditorScreen)) {
				render(graphics);
			}
		});
	}

	public static void info(Component text) {
		show(Kind.INFO, text);
	}

	public static void warning(Component text) {
		show(Kind.WARNING, text);
	}

	public static void error(Component text) {
		show(Kind.ERROR, text);
	}

	public static synchronized void show(Kind kind, Component text) {
		ACTIVE.removeIf(n -> n.kind == kind && n.text.equals(text));
		ACTIVE.add(new Notice(kind, text, Util.getMillis()));
		while (ACTIVE.size() > MAX) {
			ACTIVE.remove(0);
		}
	}

	public static synchronized boolean any() {
		return !ACTIVE.isEmpty();
	}

	public static synchronized void clear() {
		ACTIVE.clear();
	}

	/** Draws the notes that are still alive; each one is drawn in the same text size as the rest of Holoprint's HUD. */
	public static synchronized void render(GuiGraphicsExtractor graphics) {
		long now = Util.getMillis();
		ACTIVE.removeIf(n -> now - n.at >= LIFE);
		if (ACTIVE.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int screen = HudText.width(graphics);
		int wrap = Math.min(MAX_WIDTH, screen - 40) - ICON - 3 * PAD;
		HudText.begin(graphics);
		int y = TOP;
		for (Notice n : ACTIVE) {
			long age = now - n.at;
			float alpha = Math.min(Math.min(1.0F, age / (float) FADE_IN), (LIFE - age) / (float) FADE_OUT);
			if (alpha <= 0.02F) {
				continue;
			}
			List<FormattedCharSequence> lines = font.split(n.text, wrap);
			int textWidth = 0;
			for (FormattedCharSequence line : lines) {
				textWidth = Math.max(textWidth, font.width(line));
			}
			int width = textWidth + ICON + 3 * PAD;
			int height = Math.max(ICON, lines.size() * LINE - 1) + 2 * PAD;
			int x = (screen - width) / 2;
			graphics.fill(x, y, x + width, y + height, fade(0xE0101010, alpha));
			graphics.fill(x, y, x + 2, y + height, fade(n.kind.color, alpha));
			int iconY = y + (height - ICON) / 2;
			graphics.fill(x + PAD + 1, iconY, x + PAD + 1 + ICON, iconY + ICON, fade(n.kind.color, alpha));
			graphics.text(font, n.kind.mark, x + PAD + 1 + (ICON - font.width(n.kind.mark)) / 2, iconY + 2, fade(0xFF101010, alpha), false);
			int textY = y + (height - (lines.size() * LINE - 1)) / 2;
			for (FormattedCharSequence line : lines) {
				graphics.text(font, line, x + 2 * PAD + ICON + 1, textY, fade(0xFFFFFFFF, alpha), false);
				textY += LINE;
			}
			y += height + GAP;
		}
		HudText.end(graphics);
	}

	/** The colour with its opacity scaled; never fully transparent, because the game draws text with almost no opacity as if it were solid. */
	private static int fade(int argb, float alpha) {
		int a = Math.max(8, Math.round((argb >>> 24) * alpha));
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
