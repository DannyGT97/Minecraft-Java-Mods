package io.github.dannygt97.holoprint.ui.panel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.hologram.Verifier;
import io.github.dannygt97.holoprint.schematic.SchematicFolder;
import io.github.dannygt97.holoprint.ui.MaterialsPin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The list of blocks the active hologram still needs: for each kind of block how many it asks for, how
 * many are already built in the world, how many are missing and how many the player carries. It is
 * refreshed from the world while it is open, so it counts down as the build goes up.
 */
public final class MaterialsPanel extends EditorPanel {
	private static final int NOMINAL_WIDTH = 640;
	private static final int ROW_HEIGHT = 13;
	private static final int HEAD = 54;
	/** The size of the item icons. */
	private static final int ICON = 11;
	/** The least room the names must keep for the column of boxes to be shown. */
	private static final int MIN_NAME = 70;

	/** Kept between openings. */
	private static boolean onlyMissing = true;

	private final int WIDTH = Math.min(NOMINAL_WIDTH, availableWidth());
	private final int rows = Math.max(4, Math.min(24, (available() - 26 - HEAD - 84) / ROW_HEIGHT));
	private final Hologram hologram = HologramRenderer.active();
	private int scroll;

	@Override
	public Component title() {
		return Component.translatable("holoprint.panel.materials");
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public int height() {
		return 26 + HEAD + rows * ROW_HEIGHT + 84;
	}

	private List<Verifier.Row> shown() {
		Verifier.Result result = hologram == null ? null : hologram.verification();
		List<Verifier.Row> out = new ArrayList<>();
		if (result != null && result.matches(hologram.origin(), hologram.quarterTurns(), hologram.mirrored())) {
			for (Verifier.Row row : MaterialsPin.sorted(result.rows)) {
				if (!onlyMissing || row.pending() > 0) {
					out.add(row);
				}
			}
		}
		return out;
	}

	@Override
	public void init(Host host, int x, int y) {
		int footerY = y + HEAD + rows * ROW_HEIGHT + 8;
		int w = (WIDTH - 2 * PAD - 3 * 4) / 4;
		button(host, x + PAD, footerY, w, Component.translatable("gui.back"), host::back);
		button(host, x + PAD + (w + 4), footerY, w, Component.translatable("holoprint.materials.refresh"), () -> {
			if (hologram != null) {
				Verifier.restart(hologram);
			}
		});
		button(host, x + PAD + 2 * (w + 4), footerY, w, Component.translatable("holoprint.materials.only_missing",
			Component.translatable(onlyMissing ? "options.on" : "options.off")), () -> {
				onlyMissing = !onlyMissing;
				scroll = 0;
				host.rebuild();
			});
		button(host, x + PAD + 3 * (w + 4), footerY, w, Component.translatable("holoprint.materials.export"), this::export);
		// The second row: the order of the lists (the window and the pinned one) and whether the list stays on the screen.
		int w2 = (WIDTH - 2 * PAD - 4) / 2;
		button(host, x + PAD, footerY + 24, w2, Component.translatable("holoprint.materials.sort", MaterialsPin.sort().label()), () -> {
			MaterialsPin.cycleSort();
			scroll = 0;
			host.rebuild();
		});
		button(host, x + PAD + w2 + 4, footerY + 24, w2, Component.translatable("holoprint.materials.pin",
			Component.translatable(MaterialsPin.pinned() ? "options.on" : "options.off")), () -> {
				MaterialsPin.togglePinned();
				host.rebuild();
			});
		Button replace = button(host, x + PAD, footerY + 48, WIDTH - 2 * PAD, Component.translatable("holoprint.materials.replace"), () -> host.open(new ReplacePanel()));
		replace.active = hologram != null;
	}

	private void export() {
		Minecraft mc = Minecraft.getInstance();
		Verifier.Result result = hologram == null ? null : hologram.verification();
		if (result == null || mc.player == null) {
			return;
		}
		StringBuilder text = new StringBuilder();
		text.append(hologram.displayName()).append('\n');
		for (Verifier.Row row : MaterialsPin.sorted(result.rows)) {
			text.append(row.name().getString()).append(": ").append(row.needed()).append(" needed, ").append(row.placed()).append(" placed, ")
				.append(row.pending()).append(" missing (").append(MaterialsPin.pack(row, row.pending())).append(")").append(row.unchecked() > 0 ? ", " + row.unchecked() + " unchecked (not loaded)" : "").append('\n');
		}
		String base = hologram.displayName().isBlank() ? "materials" : hologram.displayName().replaceAll("[^A-Za-z0-9 _.-]", "_");
		Path file = SchematicFolder.path().resolve(base + "_materials.txt");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text.toString());
			mc.player.sendSystemMessage(Component.translatable("holoprint.materials.exported", file.getFileName().toString()));
		} catch (IOException e) {
			mc.player.sendSystemMessage(Component.translatable("holoprint.message.load_failed", String.valueOf(e.getMessage())));
		}
	}

	@Override
	public boolean mouseScrolled(Host host, double wheel) {
		int max = Math.max(0, shown().size() - rows);
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(wheel) * 2));
		return true;
	}

	private static Map<Item, Integer> carried() {
		return MaterialsPin.carried();
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		if (hologram == null) {
			text(graphics, Component.translatable("holoprint.materials.no_hologram"), x + PAD, y + 8, MUTED);
			return;
		}
		Verifier.request(hologram);
		Verifier.Result result = hologram.verification();
		boolean fresh = result != null && result.matches(hologram.origin(), hologram.quarterTurns(), hologram.mirrored());
		text(graphics, font().plainSubstrByWidth(hologram.displayName(), WIDTH - 2 * PAD), x + PAD, y, GOLD);
		if (!fresh) {
			text(graphics, Component.translatable("holoprint.materials.checking", Verifier.progress(hologram)), x + PAD, y + 14, MUTED);
			text(graphics, Component.translatable("holoprint.materials.checking_big"), x + PAD, y + 26, MUTED);
			return;
		}
		long percent = result.needed == 0 ? 100 : result.placed * 100 / result.needed;
		text(graphics, font().plainSubstrByWidth(Component.translatable("holoprint.materials.summary", result.placed, result.needed, percent, result.missing).getString()
			+ "   " + Component.translatable("holoprint.materials.boxes", MaterialsPin.boxes(result.rows, true)).getString(), WIDTH - 2 * PAD), x + PAD, y + 13, TEXT);
		StringBuilder notes = new StringBuilder();
		if (result.wrongCount > 0) {
			notes.append(Component.translatable("holoprint.materials.wrong", result.wrongCount).getString()).append("   ");
		}
		if (result.wrongStateCount > 0) {
			notes.append(Component.translatable("holoprint.materials.wrong_state", result.wrongStateCount).getString()).append("   ");
		}
		if (result.wrongSettingCount > 0) {
			notes.append(Component.translatable("holoprint.materials.wrong_setting", result.wrongSettingCount).getString()).append("   ");
		}
		if (result.unloaded > 0) {
			notes.append(Component.translatable("holoprint.materials.unloaded", result.unloaded).getString());
		}
		if (!notes.isEmpty()) {
			text(graphics, font().plainSubstrByWidth(notes.toString(), WIDTH - 2 * PAD), x + PAD, y + 25, 0xFFFFA040);
		}

		int packX = x + WIDTH - 380;
		int nameX = x + PAD, neededX = x + WIDTH - 250, placedX = x + WIDTH - 190, missingX = x + WIDTH - 130, carriedX = x + WIDTH - 70;
		// In a narrow window the column of boxes would leave no room for the names, so it is left out.
		boolean showPack = packX - nameX - 8 - ICON - 3 >= MIN_NAME;
		int headY = y + 40;
		text(graphics, Component.translatable("holoprint.materials.col_block"), nameX, headY, MUTED);
		if (showPack) {
			right(graphics, Component.translatable("holoprint.materials.col_pack").getString(), packX + 120, headY, MUTED);
		}
		right(graphics, Component.translatable("holoprint.materials.col_needed").getString(), neededX + 52, headY, MUTED);
		right(graphics, Component.translatable("holoprint.materials.col_placed").getString(), placedX + 52, headY, MUTED);
		right(graphics, Component.translatable("holoprint.materials.col_missing").getString(), missingX + 52, headY, MUTED);
		right(graphics, Component.translatable("holoprint.materials.col_carried").getString(), carriedX + 52, headY, MUTED);
		graphics.fill(x + PAD, headY + 11, x + WIDTH - PAD, headY + 12, 0x55FFFFFF);

		List<Verifier.Row> list = shown();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
		Map<Item, Integer> carried = carried();
		boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasInfiniteMaterials();
		int top = y + HEAD;
		if (list.isEmpty()) {
			text(graphics, Component.translatable(onlyMissing ? "holoprint.materials.all_done" : "holoprint.materials.empty"), nameX, top + 4, MUTED);
		}
		for (int i = 0; i < rows && scroll + i < list.size(); i++) {
			Verifier.Row row = list.get(scroll + i);
			int rowY = top + i * ROW_HEIGHT;
			if (i % 2 == 0) {
				graphics.fill(x + PAD, rowY - 1, x + WIDTH - PAD, rowY + ROW_HEIGHT - 1, 0x22FFFFFF);
			}
			MaterialsPin.icon(graphics, MaterialsPin.stackOf(row), nameX, rowY, ICON);
			text(graphics, font().plainSubstrByWidth(row.name().getString(), (showPack ? packX : neededX) - nameX - 8 - ICON - 3), nameX + ICON + 3, rowY + 1, row.pending() == 0 ? 0xFF70D070 : TEXT);
			if (showPack) {
				MaterialsPin.drawPack(graphics, font(), row, row.needed(), packX + 120, rowY + 1, MUTED, ICON);
			}
			right(graphics, String.valueOf(row.needed()), neededX + 52, rowY + 1, TEXT);
			right(graphics, String.valueOf(row.placed()), placedX + 52, rowY + 1, TEXT);
			right(graphics, String.valueOf(row.pending()), missingX + 52, rowY + 1, row.pending() == 0 ? 0xFF70D070 : GOLD);
			int have = row.key() instanceof Item item ? carried.getOrDefault(item, 0) : 0;
			boolean enough = creative || have >= row.pending();
			// In creative mode the items are free, but what is in the inventory is still shown.
				boolean show = row.key() instanceof Item && (!creative || have > 0);
				right(graphics, show ? String.valueOf(have) : "-", carriedX + 52, rowY + 1, row.pending() == 0 ? MUTED : enough ? 0xFF70D070 : 0xFFFF7070);
		}
		if (list.size() > rows) {
			text(graphics, Component.translatable("holoprint.materials.more", list.size() - rows), x + WIDTH - 150, y + HEAD + rows * ROW_HEIGHT - 1, MUTED);
		}
	}

	private static void right(GuiGraphicsExtractor graphics, String text, int rightEdge, int y, int color) {
		text(graphics, text, rightEdge - font().width(text), y, color);
	}
}
