package io.github.dannygt97.holoprint.ui.panel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import io.github.dannygt97.holoprint.placement.PlacementLoader;
import io.github.dannygt97.holoprint.schematic.LitematicFormat;
import io.github.dannygt97.holoprint.schematic.SchematicFolder;
import io.github.dannygt97.holoprint.schematic.SchematicFormats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Picks a schematic from the schematics folder. The files are found by themselves (no typing of
 * names), can be searched, and the one that is picked shows what the file says about itself on the
 * right. Pressing Load adds it as a new placement.
 */
public final class LoadPanel extends EditorPanel {
	private static final int NOMINAL_WIDTH = 720;
	private static final int ROW_HEIGHT = 20;

	private record Entry(Path file, long size, long modified) {}

	private final int WIDTH = Math.min(NOMINAL_WIDTH, availableWidth());
	private final int listWidth = (WIDTH - 2 * PAD - 8) * 55 / 100;
	private final int rows = Math.max(3, Math.min(16, (available() - 26 - 26 - 58) / ROW_HEIGHT));
	private final List<Entry> files = new ArrayList<>();
	private final List<Entry> shown = new ArrayList<>();
	private final List<Button> rowButtons = new ArrayList<>();
	private String filter = "";
	private int scroll;
	private Entry selected;

	/** What the file said about itself, read in the background; null while it is being read. */
	private volatile LitematicFormat.Info info;
	private volatile String infoError;
	private volatile Path infoFor;

	@Override
	public Component title() {
		return Component.translatable("holoprint.panel.load");
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public int height() {
		return 26 + 26 + rows * ROW_HEIGHT + 58;
	}

	private void scan() {
		files.clear();
		try (Stream<Path> stream = Files.list(SchematicFolder.path())) {
			stream.filter(Files::isRegularFile).filter(LoadPanel::looksLikeSchematic).forEach(path -> {
				try {
					files.add(new Entry(path, Files.size(path), Files.getLastModifiedTime(path).toMillis()));
				} catch (IOException ignored) {
					// a file that vanished while listing is simply left out
				}
			});
		} catch (IOException ignored) {
			// no folder yet: the list stays empty and says so
		}
		files.sort(Comparator.comparing((Entry e) -> e.file().getFileName().toString().toLowerCase(Locale.ROOT)));
	}

	private static boolean looksLikeSchematic(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".litematic") || name.endsWith(".schematic") || name.endsWith(".schem");
	}

	private void applyFilter() {
		shown.clear();
		for (Entry entry : files) {
			if (filter.isEmpty() || entry.file().getFileName().toString().toLowerCase(Locale.ROOT).contains(filter)) {
				shown.add(entry);
			}
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - rows)));
		for (int i = 0; i < rowButtons.size(); i++) {
			Button button = rowButtons.get(i);
			int index = scroll + i;
			button.visible = index < shown.size();
			if (button.visible) {
				button.setMessage(Component.literal(shown.get(index).file().getFileName().toString()));
			}
		}
	}

	/** Picks a file and reads its description in the background. */
	private void select(Entry entry) {
		selected = entry;
		info = null;
		infoError = null;
		infoFor = entry.file();
		if (!PlacementLoader.isSupported(entry.file())) {
			return;
		}
		Path file = entry.file();
		new Thread(() -> {
			try {
				LitematicFormat.Info read = SchematicFormats.readInfo(file);
				if (file.equals(infoFor)) {
					info = read;
				}
			} catch (IOException | RuntimeException e) {
				if (file.equals(infoFor)) {
					infoError = String.valueOf(e.getMessage());
				}
			}
		}, "Holoprint file info").start();
	}

	@Override
	public void init(Host host, int x, int y) {
		scan();
		rowButtons.clear();
		EditBox search = box(host, x + PAD, y, listWidth, "");
		search.setHint(Component.translatable("holoprint.load.search"));
		search.setResponder(text -> {
			filter = text.toLowerCase(Locale.ROOT).trim();
			scroll = 0;
			applyFilter();
		});
		host.focus(search);
		for (int i = 0; i < rows; i++) {
			int slot = i;
			Button row = host.add(new RowButton(x + PAD, y + 26 + i * ROW_HEIGHT, listWidth, ROW_HEIGHT - 2, () -> {
				int index = scroll + slot;
				if (index < shown.size()) {
					select(shown.get(index));
				}
			}, () -> {
				int index = scroll + slot;
				return index < shown.size() && shown.get(index) == selected;
			}));
			rowButtons.add(row);
		}
		applyFilter();
		int footerY = y + 26 + rows * ROW_HEIGHT + 34;
		button(host, x + PAD, footerY, 100, Component.translatable("gui.back"), host::back);
		button(host, x + WIDTH - PAD - 160, footerY, 160, Component.translatable("holoprint.load.load"), () -> {
			if (selected != null && PlacementLoader.isSupported(selected.file())) {
				PlacementLoader.load(Minecraft.getInstance(), selected.file());
				host.closeAll();
			}
		});
	}

	@Override
	public boolean mouseScrolled(Host host, double wheel) {
		int max = Math.max(0, shown.size() - rows);
		int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(wheel)));
		if (next == scroll) {
			return max > 0;
		}
		scroll = next;
		applyFilter();
		return true;
	}

	private static String date(long millis) {
		return millis <= 0 ? "-" : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		if (files.isEmpty()) {
			text(graphics, Component.translatable("holoprint.load.empty", SchematicFolder.path().getFileName().toString()), x + PAD, y + 40, MUTED);
		} else if (shown.isEmpty()) {
			text(graphics, Component.translatable("holoprint.load.no_match"), x + PAD, y + 40, MUTED);
		}

		// The description of the picked file, in its own box on the right.
		int ix = x + PAD + listWidth + 8;
		int iw = WIDTH - 2 * PAD - listWidth - 8;
		int ih = rows * ROW_HEIGHT + 24;
		graphics.fill(ix, y, ix + iw, y + ih, 0x55000000);
		int ty = y + 6;
		if (selected == null) {
			text(graphics, Component.translatable("holoprint.load.pick"), ix + 6, ty, MUTED);
			return;
		}
		for (FormattedCharSequence line : font().split(Component.literal(selected.file().getFileName().toString()), iw - 12)) {
			graphics.text(font(), line, ix + 6, ty, GOLD, false);
			ty += 10;
		}
		ty += 4;
		LitematicFormat.Info shownInfo = info;
		if (!PlacementLoader.isSupported(selected.file())) {
			ty = lines(graphics, ix + 6, ty, iw - 12, 0xFFFF9070, Component.translatable("holoprint.load.unsupported"));
		} else if (infoError != null) {
			ty = lines(graphics, ix + 6, ty, iw - 12, 0xFFFF7070, Component.translatable("holoprint.load.unreadable", infoError));
		} else if (shownInfo == null) {
			ty = lines(graphics, ix + 6, ty, iw - 12, MUTED, Component.translatable("holoprint.load.reading"));
		} else {
			if (!shownInfo.name().isBlank() && !shownInfo.name().equals("Unnamed")) {
				ty = lines(graphics, ix + 6, ty, iw - 12, TEXT, Component.translatable("holoprint.load.info.name", shownInfo.name()));
			}
			if (!shownInfo.author().isBlank()) {
				ty = lines(graphics, ix + 6, ty, iw - 12, TEXT, Component.translatable("holoprint.load.info.author", shownInfo.author()));
			}
			ty = lines(graphics, ix + 6, ty, iw - 12, TEXT, Component.translatable("holoprint.load.info.size", shownInfo.sizeX(), shownInfo.sizeY(), shownInfo.sizeZ()));
			ty = lines(graphics, ix + 6, ty, iw - 12, TEXT, Component.translatable("holoprint.load.info.blocks", String.format("%,d", shownInfo.totalBlocks())));
			ty = lines(graphics, ix + 6, ty, iw - 12, MUTED, Component.translatable("holoprint.load.info.regions", shownInfo.regionCount()));
			ty = lines(graphics, ix + 6, ty, iw - 12, MUTED, Component.translatable("holoprint.load.info.created", date(shownInfo.timeCreated())));
			ty = lines(graphics, ix + 6, ty, iw - 12, MUTED, Component.translatable("holoprint.load.info.modified", date(shownInfo.timeModified())));
			if (!shownInfo.description().isBlank()) {
				ty = lines(graphics, ix + 6, ty + 4, iw - 12, MUTED, Component.literal(shownInfo.description()));
			}
		}
		text(graphics, font().plainSubstrByWidth(Component.translatable("holoprint.load.detail", selected.size() / 1024 + 1, date(selected.modified())).getString(), iw - 12), ix + 6, y + ih - 12, MUTED);
	}

	/** Draws wrapped text and returns the y below it; text that would run off the box is left out. */
	private int lines(GuiGraphicsExtractor graphics, int x, int y, int width, int color, Component text) {
		int limit = 26 + rows * ROW_HEIGHT - 4;
		for (FormattedCharSequence line : font().split(text, width)) {
			if (y > limit) {
				break;
			}
			graphics.text(font(), line, x, y, color, false);
			y += 10;
		}
		return y;
	}

	/** One row of the file list. */
	private static final class RowButton extends Button {
		private final java.util.function.BooleanSupplier current;

		RowButton(int x, int y, int width, int height, Runnable action, java.util.function.BooleanSupplier current) {
			super(x, y, width, height, Component.empty(), b -> action.run(), DEFAULT_NARRATION);
			this.current = current;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			if (current.getAsBoolean()) {
				graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x55FFD166);
			} else if (isHoveredOrFocused()) {
				graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x30FFFFFF);
			} else {
				graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x33000000);
			}
			String text = Minecraft.getInstance().font.plainSubstrByWidth(getMessage().getString(), getWidth() - 10);
			graphics.text(Minecraft.getInstance().font, text, getX() + 6, getY() + (getHeight() - 9) / 2 + 1, 0xFFFFFFFF, false);
		}
	}
}
