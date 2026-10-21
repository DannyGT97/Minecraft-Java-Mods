package io.github.dannygt97.holoprint.ui.panel;

import java.util.List;

import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.hologram.Verifier;
import io.github.dannygt97.holoprint.placement.Editor;
import io.github.dannygt97.holoprint.placement.PlacementLimits;
import io.github.dannygt97.holoprint.ui.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The list of schematic placements: every structure that has been put in the world as a hologram.
 * Like Litematica's "Schematic Placements" menu, each row can be picked as the one to move,
 * configured, hidden, locked or removed.
 */
public final class PlacementsPanel extends EditorPanel {
	private static final int NOMINAL_WIDTH = 560;
	private static final int ROW_HEIGHT = 34;
	private static final int LIST_TOP = 28;

	private final int WIDTH = Math.min(NOMINAL_WIDTH, availableWidth());
	private final int visibleRows = Math.max(2, Math.min(9, (available() - 26 - LIST_TOP - 44) / ROW_HEIGHT));
	private int scroll;

	@Override
	public Component title() {
		return Component.translatable("holoprint.panel.placements");
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public int height() {
		return 26 + LIST_TOP + visibleRows * ROW_HEIGHT + 40;
	}

	/** The name a placement is shown by: its own, or a number when it has none. */
	public static String nameOf(Hologram hologram, int index) {
		String name = hologram.displayName();
		return name.isBlank() ? Component.translatable("holoprint.placements.unnamed", index + 1).getString() : name;
	}

	@Override
	public void init(Host host, int x, int y) {
		List<Hologram> all = HologramRenderer.all();
		button(host, x + PAD, y, 110, Component.translatable("holoprint.placements.load"), () -> host.open(new LoadPanel()));
		button(host, x + PAD + 114, y, 90, Component.translatable("holoprint.placements.hide_all"), () -> {
			boolean anyVisible = all.stream().anyMatch(Hologram::visible);
			all.forEach(h -> h.visible(!anyVisible));
			host.rebuild();
		});
		button(host, x + PAD + 208, y, 90, Component.translatable("holoprint.placements.remove_all"), () -> {
			all.stream().filter(h -> !h.locked()).toList().forEach(HologramRenderer::remove);
			host.rebuild();
		});

		scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - visibleRows)));
		for (int i = 0; i < visibleRows && scroll + i < all.size(); i++) {
			int index = scroll + i;
			Hologram hologram = all.get(index);
			int rowY = y + LIST_TOP + i * ROW_HEIGHT;
			int right = x + WIDTH - PAD - 4;
			int removeX = right - 18;
			int lockX = removeX - 26;
			int hideX = lockX - 18;
			int configX = hideX - 4 - 62;
			// the "mark as complete" box: with all its words when there is room, with a short one when there is not
			boolean roomy = WIDTH >= 520;
			int doneWidth = roomy ? 142 : 72;
			int doneX = configX - 4 - doneWidth;
			boolean done = hologram.complete();
			host.add(new RowSelect(x + PAD, rowY, doneX - 4 - (x + PAD), ROW_HEIGHT - 3, hologram, nameOf(hologram, index), () -> {
				if (hologram.complete()) {
					return;
				}
				HologramRenderer.makeActive(hologram);
				Editor.activePlacementChanged(Minecraft.getInstance());
				host.rebuild();
			}));
			host.add(new CompleteToggle(doneX, rowY + 8, doneWidth, roomy, hologram, host));
			Button configure = button(host, configX, rowY + 6, 62, Component.translatable("holoprint.placements.configure"),
				() -> host.open(new PlacementConfigPanel(hologram, nameOf(hologram, index))));
			IconButton hide = host.add(new IconButton(hideX, rowY + 8, 18, "Eye", Component.translatable(hologram.visible() ? "holoprint.placements.tip_hide" : "holoprint.placements.tip_show"),
				false, !hologram.visible(), () -> {
					hologram.visible(!hologram.visible());
					host.rebuild();
				}));
			IconButton lock = host.add(new IconButton(lockX + 4, rowY + 8, 18, hologram.locked() ? "Lock" : "Unlock",
				Component.translatable(hologram.locked() ? "holoprint.placements.tip_unlock" : "holoprint.placements.tip_lock"),
				hologram.locked(), false, () -> {
					hologram.locked(!hologram.locked());
					host.rebuild();
				}));
			if (done) {
				// a finished placement is not changed from here: each button says how to get it back
				for (Button off : new Button[] {configure, hide, lock}) {
					off.active = false;
					off.setTooltip(Tooltip.create(Component.translatable("holoprint.placements.tip_complete_locked")));
				}
			}
			host.add(new IconButton(removeX, rowY + 8, 18, "Bo", Component.translatable("holoprint.placements.remove"), false, false, () -> {
				if (hologram.locked()) {
					return;
				}
				HologramRenderer.remove(hologram);
				Editor.activePlacementChanged(Minecraft.getInstance());
				host.rebuild();
			}));
		}
		button(host, x + (WIDTH - 120) / 2, y + LIST_TOP + visibleRows * ROW_HEIGHT + 8, 120, Component.translatable("gui.back"), host::back);
	}

	@Override
	public boolean mouseScrolled(Host host, double wheel) {
		int max = Math.max(0, HologramRenderer.all().size() - visibleRows);
		if (max == 0) {
			return false;
		}
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(wheel)));
		host.rebuild();
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		List<Hologram> all = HologramRenderer.all();
		// the check of the world has to be running for the boxes to know which placements are finished
		all.forEach(Verifier::request);
		Component count = Component.translatable("holoprint.placements.count", all.size(), PlacementLimits.max());
		text(graphics, count, x + WIDTH - 30 - font().width(count), y - 17, all.size() >= PlacementLimits.max() ? 0xFFFF7070 : MUTED);
		if (all.isEmpty()) {
			text(graphics, Component.translatable("holoprint.placements.empty"), x + PAD, y + LIST_TOP + 12, MUTED);
		} else if (all.size() > visibleRows) {
			text(graphics, Component.translatable("holoprint.placements.more", all.size() - visibleRows), x + PAD, y + LIST_TOP + visibleRows * ROW_HEIGHT - 2, MUTED);
		}
	}

	/**
	 * The "mark as complete" box of a row: green and ticked when the placement is finished, white and empty when it is not.
	 * A placement the check of the world finds finished is ticked by itself; unticking it opens it again (it is drawn whole so it can be
	 * moved), and ticking it again closes it.
	 */
	private static final class CompleteToggle extends Button {
		private static final int GREEN = 0xFF55FF55;
		private final Hologram hologram;
		
		CompleteToggle(int x, int y, int width, boolean roomy, Hologram hologram, Host host) {
			super(x, y, width, 18, Component.translatable(roomy ? "holoprint.placements.complete" : "holoprint.placements.complete_short"), b -> {
				hologram.toggleComplete();
				if (hologram.reopened()) {
					// opened again to be moved: it is the one the editor works on
					HologramRenderer.makeActive(hologram);
					Editor.activePlacementChanged(Minecraft.getInstance());
				} else {
					Editor.activePlacementChanged(Minecraft.getInstance());
				}
				host.rebuild();
			}, DEFAULT_NARRATION);
			this.hologram = hologram;
			setTooltip(Tooltip.create(Component.translatable(hologram.complete() ? "holoprint.placements.tip_complete_off"
				: hologram.reopened() ? "holoprint.placements.tip_complete_reopened" : "holoprint.placements.tip_complete_on")));
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			boolean done = hologram.complete();
			int x = getX();
			int y = getY();
			int color = done ? GREEN : 0xFFFFFFFF;
			graphics.fill(x, y, x + getWidth(), y + getHeight(), isHoveredOrFocused() ? 0x66FFFFFF : 0x55000000);
			int bx = x + 3;
			int by = y + 4;
			graphics.fill(bx, by, bx + 10, by + 10, color);
			graphics.fill(bx + 1, by + 1, bx + 9, by + 9, 0xFF101010);
			if (done) {
				for (int i = 0; i < 3; i++) {
					graphics.fill(bx + 2 + i, by + 4 + i, bx + 3 + i, by + 5 + i, color);
				}
				for (int i = 0; i < 4; i++) {
					graphics.fill(bx + 4 + i, by + 6 - i, bx + 5 + i, by + 7 - i, color);
				}
			}
			var font = Minecraft.getInstance().font;
			graphics.text(font, font.plainSubstrByWidth(getMessage().getString(), getWidth() - 20), x + 17, y + 5, color, false);
		}
	}

	/**
	 * How many blocks of the placement could not be checked because the client does not have that part of the world (it is far from the player, though the free
	 * camera can look at it), when everything that could be checked is built; 0 otherwise.
	 */
	private static long unchecked(Hologram hologram) {
		var result = hologram.verification();
		if (hologram.reopened() || result == null || !result.matches(hologram.origin(), hologram.quarterTurns(), hologram.mirrored())) {
			return 0;
		}
		return result.missing == 0 && result.wrongCount == 0 && result.wrongStateCount == 0 && result.placed > 0 ? result.unloaded : 0;
	}

	/** The name and details of one placement; pressing it makes that placement the one Move works on. */
	private static final class RowSelect extends Button {
		private final Hologram hologram;
		private final String name;

		RowSelect(int x, int y, int width, int height, Hologram hologram, String name, Runnable action) {
			super(x, y, width, height, Component.literal(name), b -> action.run(), DEFAULT_NARRATION);
			this.hologram = hologram;
			this.name = name;
			if (hologram.complete()) {
				setTooltip(Tooltip.create(Component.translatable("holoprint.placements.tip_complete_row")));
			} else if (unchecked(hologram) > 0) {
				setTooltip(Tooltip.create(Component.translatable("holoprint.placements.tip_unchecked", unchecked(hologram))));
			}
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			boolean current = hologram == HologramRenderer.active();
			int x = getX();
			int y = getY();
			graphics.fill(x, y, x + getWidth(), y + getHeight(), isHoveredOrFocused() ? 0x66FFFFFF : 0x55000000);
			if (current) {
				graphics.fill(x, y, x + 2, y + getHeight(), 0xFFFFD166);
			}
			var font = Minecraft.getInstance().font;
			String shown = font.plainSubstrByWidth((current ? "★ " : "") + name + (hologram.visible() ? "" : " (hidden)"), getWidth() - 10);
			boolean done = hologram.complete();
			graphics.text(font, shown, x + 6, y + 5, !hologram.visible() || done ? 0xFF808080 : current ? 0xFFFFD166 : 0xFFE0E0E0, false);
			BlockPos o = hologram.origin();
			String detail = font.plainSubstrByWidth(o.getX() + ", " + o.getY() + ", " + o.getZ() + "   " + hologram.sizeX() + "x"
				+ hologram.sizeY() + "x" + hologram.sizeZ() + "   " + hologram.quarterTurns() * 90 + "°", getWidth() - 10);
			graphics.text(font, detail, x + 6, y + 18, 0xFF909090, false);
			// what state it is in, after the details: finished (green) or opened again (yellow)
			long unchecked = done ? 0 : unchecked(hologram);
			if (done || hologram.reopened() || unchecked > 0) {
				int statusX = x + 6 + font.width(detail) + 10;
				String status = (done ? Component.translatable("holoprint.placements.status_complete") : hologram.reopened() ? Component.translatable("holoprint.placements.status_reopened")
					: Component.translatable("holoprint.placements.status_unchecked", unchecked)).getString();
				// only when it fits whole: a cut word says nothing (the row is dimmed and its tooltip tells it anyway)
				if (statusX + font.width(status) <= getX() + getWidth() - 4) {
					graphics.text(font, status, statusX, y + 18, done ? 0xFF55FF55 : 0xFFFFD166, false);
				}
			}
		}
	}
}
