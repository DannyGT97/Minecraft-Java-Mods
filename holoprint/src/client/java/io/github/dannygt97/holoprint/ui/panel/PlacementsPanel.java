package io.github.dannygt97.holoprint.ui.panel;

import java.util.List;

import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.placement.Editor;
import io.github.dannygt97.holoprint.placement.PlacementLimits;
import io.github.dannygt97.holoprint.ui.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
			host.add(new RowSelect(x + PAD, rowY, configX - 4 - (x + PAD), ROW_HEIGHT - 3, hologram, nameOf(hologram, index), () -> {
				HologramRenderer.makeActive(hologram);
				Editor.activePlacementChanged(Minecraft.getInstance());
				host.rebuild();
			}));
			button(host, configX, rowY + 6, 62, Component.translatable("holoprint.placements.configure"),
				() -> host.open(new PlacementConfigPanel(hologram, nameOf(hologram, index))));
			host.add(new IconButton(hideX, rowY + 8, 18, "Eye", Component.translatable(hologram.visible() ? "holoprint.placements.tip_hide" : "holoprint.placements.tip_show"),
				false, !hologram.visible(), () -> {
					hologram.visible(!hologram.visible());
					host.rebuild();
				}));
			host.add(new IconButton(lockX + 4, rowY + 8, 18, hologram.locked() ? "Lock" : "Unlock",
				Component.translatable(hologram.locked() ? "holoprint.placements.tip_unlock" : "holoprint.placements.tip_lock"),
				hologram.locked(), false, () -> {
					hologram.locked(!hologram.locked());
					host.rebuild();
				}));
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
		Component count = Component.translatable("holoprint.placements.count", all.size(), PlacementLimits.max());
		text(graphics, count, x + WIDTH - 30 - font().width(count), y - 17, all.size() >= PlacementLimits.max() ? 0xFFFF7070 : MUTED);
		if (all.isEmpty()) {
			text(graphics, Component.translatable("holoprint.placements.empty"), x + PAD, y + LIST_TOP + 12, MUTED);
		} else if (all.size() > visibleRows) {
			text(graphics, Component.translatable("holoprint.placements.more", all.size() - visibleRows), x + PAD, y + LIST_TOP + visibleRows * ROW_HEIGHT - 2, MUTED);
		}
	}

	/** The name and details of one placement; pressing it makes that placement the one Move works on. */
	private static final class RowSelect extends Button {
		private final Hologram hologram;
		private final String name;

		RowSelect(int x, int y, int width, int height, Hologram hologram, String name, Runnable action) {
			super(x, y, width, height, Component.literal(name), b -> action.run(), DEFAULT_NARRATION);
			this.hologram = hologram;
			this.name = name;
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
			graphics.text(font, shown, x + 6, y + 5, !hologram.visible() ? 0xFF808080 : current ? 0xFFFFD166 : 0xFFE0E0E0, false);
			BlockPos o = hologram.origin();
			String detail = font.plainSubstrByWidth(o.getX() + ", " + o.getY() + ", " + o.getZ() + "   " + hologram.sizeX() + "x"
				+ hologram.sizeY() + "x" + hologram.sizeZ() + "   " + hologram.quarterTurns() * 90 + "°", getWidth() - 10);
			graphics.text(font, detail, x + 6, y + 18, 0xFF909090, false);
		}
	}
}
