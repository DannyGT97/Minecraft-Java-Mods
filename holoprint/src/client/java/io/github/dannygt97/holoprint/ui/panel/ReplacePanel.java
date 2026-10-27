package io.github.dannygt97.holoprint.ui.panel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.ui.MaterialsPin;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Replace all": every block of one kind in the active hologram becomes another block (the one held in the hand, or air). Only the
 * hologram changes, what it asks the world to look like: the schematic file and the world are not touched. The replacements made are
 * listed so each can be taken back.
 */
public final class ReplacePanel extends EditorPanel {
	private static final int NOMINAL_WIDTH = 420;
	private static final int LIST_ROW = 16;
	private static final int ICON = 11;
	/** The side of a square of the grid the new block is picked from. */
	private static final int CELL = 18;
	/** The gap kept below the bottom row of buttons, so they do not touch the edge of the window. */
	private static final int BOTTOM_MARGIN = 8;
	private static final int SCROLLBAR = 4;
	/** Where the list of the hologram's blocks starts, below the placement's name (the picker keeps its own, taller head). */
	private static final int LIST_HEAD = 24;
	/** The most replacements listed; the rest are counted. */
	private static final int MADE_ROWS = 3;

	/** Kept between openings: whether the properties both blocks have (facing, half...) stay as they were. */
	private static boolean keepProperties = true;

	private final int WIDTH = Math.min(NOMINAL_WIDTH, availableWidth());
	private final int rows = Math.max(3, Math.min(8, (available() - 26 - 166) / LIST_ROW));
	/** The rows of the list of the hologram's blocks: one more than the window is sized for, as the two lines that named both blocks are gone. */
	private final int listRows = rows + 1;
	private final Hologram hologram = HologramRenderer.active();
	/** Every kind of block of the hologram and how many there are of it, the most numerous first. */
	private final Map<Block, Long> kinds = new LinkedHashMap<>();
	private Block selected;
	/** The frame of the chosen row of the list: bright, so it stands out from the button's own colours. */
	private static final int CHOSEN = 0xFF55E070;
	/** The block the selected kind becomes; null while none was picked (the block in the hand is used then). */
	private Block destination;
	/** Showing the list of every block to pick the new one from, instead of the blocks of the hologram. */
	private boolean choosing;
	private String filter = "";
	private List<Block> choices = List.of();
	private int scroll;
	/** Where the list of kinds was scrolled to when the block picker opened, so coming back from it leaves the list where it was. */
	private int listScroll;
	/** Where the grid of blocks is drawn and how many squares fit in it, set when the window is built. */
	private int gridX, gridY, gridWidth, columns = 1, gridRows = 1;
	private Component status = Component.empty();
	/** The scrollbar of the grid is being dragged with the mouse button held. */
	private boolean dragging;

	@Override
	public Component title() {
		return Component.translatable("holoprint.panel.replace");
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public int height() {
		return 26 + 40 + rows * LIST_ROW + 26 + 14 + MADE_ROWS * LIST_ROW + 28 + BOTTOM_MARGIN;
	}

	/** The top of the bottom row of buttons. */
	private int backY(int y) {
		return y + height() - 26 - 20 - BOTTOM_MARGIN;
	}

	private void count() {
		kinds.clear();
		if (hologram == null) {
			return;
		}
		Map<Block, Long> counts = new java.util.HashMap<>();
		for (int cell : hologram.solidCells()) {
			BlockState state = hologram.gridState(cell);
			if (state != null && !Hologram.flowing(state)) {
				counts.merge(state.getBlock(), 1L, Long::sum);
			}
		}
		counts.entrySet().stream().sorted(Map.Entry.<Block, Long>comparingByValue().reversed()).forEach(e -> kinds.put(e.getKey(), e.getValue()));
		if (selected != null && !kinds.containsKey(selected)) {
			selected = null;
		}
	}

	/** Every block that has an item, plus water and lava (their blocks have none), by name, to pick the new block from. */
	private static List<Block> allBlocks() {
		List<Block> blocks = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (block != Blocks.AIR && (block.asItem() != Items.AIR || block instanceof net.minecraft.world.level.block.LiquidBlock)) {
				blocks.add(block);
			}
		}
		blocks.sort(java.util.Comparator.comparing(block -> nameOf(block).getString()));
		return blocks;
	}

	private void applyFilter() {
		List<Block> found = new ArrayList<>();
		if (filter.isEmpty() || nameOf(Blocks.AIR).getString().toLowerCase(Locale.ROOT).contains(filter)) {
			found.add(Blocks.AIR);
		}
		for (Block block : allBlocks()) {
			if (filter.isEmpty() || nameOf(block).getString().toLowerCase(Locale.ROOT).contains(filter)) {
				found.add(block);
			}
		}
		choices = found;
	}

	/** The block the selected kind becomes: the one picked in the list, or else the one in the main hand. */
	private Block target() {
		return destination != null ? destination : held();
	}

	/** The block in the main hand, or null when it holds no block. */
	private static Block held() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.getMainHandItem().getItem() instanceof BlockItem item ? item.getBlock() : null;
	}

	/** The icon of a block: its item, the bucket for a fluid (water has no item) and the barrier for air. */
	private static ItemStack stackOf(Block block) {
		if (block == Blocks.AIR) {
			return new ItemStack(Items.BARRIER);
		}
		Item item = block.asItem();
		if (item == Items.AIR) {
			item = block.defaultBlockState().getFluidState().getType().getBucket();
		}
		return new ItemStack(item);
	}

	private static Component nameOf(Block block) {
		return block.getName();
	}

	/** A row of the list: the kind and how many there are. The chosen one reads "old x13 → new x13", the old in red and the new in green. */
	private Component rowLabel(Block block, boolean chosen) {
		int room = WIDTH - 2 * PAD - ICON - 12;
		String count = "  x" + kinds.get(block);
		if (!chosen) {
			return Component.literal(font().plainSubstrByWidth(nameOf(block).getString() + count, room));
		}
		Block with = target();
		boolean known = with != null && with != block;
		String arrow = "  →  ";
		int names = (room - font().width(arrow) - 2 * font().width(count)) / 2;
		MutableComponent label = Component.literal(font().plainSubstrByWidth(nameOf(block).getString(), names) + count).withStyle(ChatFormatting.RED);
		label.append(Component.literal(arrow).withStyle(ChatFormatting.WHITE));
		label.append(known ? Component.literal(font().plainSubstrByWidth(nameOf(with).getString(), names) + count).withStyle(ChatFormatting.GREEN)
			: Component.literal("?").withStyle(ChatFormatting.GOLD));
		return label;
	}

	@Override
	public void init(Host host, int x, int y) {
		count();
		if (choosing && hologram != null) {
			initChoosing(host, x, y);
			return;
		}
		int footerY = y + LIST_HEAD + listRows * LIST_ROW + 4;
		int w = (WIDTH - 2 * PAD - 2 * 4) / 3;
		if (hologram != null) {
			int top = y + LIST_HEAD;
			List<Block> list = new ArrayList<>(kinds.keySet());
			scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - listRows)));
			for (int i = 0; i < listRows && scroll + i < list.size(); i++) {
				Block block = list.get(scroll + i);
				boolean chosen = block == selected;
				Button row = host.add(Button.builder(rowLabel(block, chosen), b -> {
					selected = block;
					status = Component.empty();
					host.rebuild();
				}).bounds(x + PAD + ICON + 4, top + i * LIST_ROW, WIDTH - 2 * PAD - ICON - 4, LIST_ROW - 1).build());
				// the chosen row stays lit (its green frame is drawn in render) until the replacement is made
				row.setOverrideRenderHighlightedSprite(() -> chosen);
			}
			Block with = target();
			button(host, x + PAD, footerY, w, Component.translatable("holoprint.replace.keep", Component.translatable(keepProperties ? "options.on" : "options.off")), () -> {
				keepProperties = !keepProperties;
				host.rebuild();
			});
			Button replace = button(host, x + PAD + w + 4, footerY, w, Component.translatable("holoprint.replace.do"), () -> apply(target(), host));
			replace.active = selected != null && with != null && with != selected;
			Button air = button(host, x + PAD + 2 * (w + 4), footerY, w, Component.translatable("holoprint.replace.air"), () -> apply(Blocks.AIR, host));
			air.active = selected != null;

			int madeY = footerY + 26 + 14;
			List<Hologram.Replacement> made = hologram.replacements();
			for (int i = 0; i < MADE_ROWS && i < made.size(); i++) {
				int index = made.size() - 1 - i;
				small(host, x + WIDTH - PAD - 16, madeY + i * LIST_ROW, 16, "x", () -> {
					hologram.removeReplacement(index);
					status = Component.translatable("holoprint.replace.taken_back");
					host.rebuild();
				});
			}
		}
		int backY = backY(y);
		int w2 = (WIDTH - 2 * PAD - 2 * 4) / 3;
		button(host, x + PAD, backY, w2, Component.translatable("gui.back"), host::back);
		Button choose = button(host, x + PAD + w2 + 4, backY, w2, Component.translatable("holoprint.replace.choose"), () -> {
			choosing = true;
			filter = "";
			listScroll = scroll;
			scroll = 0;
			applyFilter();
			host.rebuild();
		});
		choose.active = hologram != null;
		Button restore = button(host, x + PAD + 2 * (w2 + 4), backY, w2, Component.translatable("holoprint.replace.restore"), () -> {
			hologram.clearReplacements();
			selected = null;
			status = Component.translatable("holoprint.replace.restored");
			host.rebuild();
		});
		restore.active = hologram != null && !hologram.replacements().isEmpty();
	}

	/**
	 * The view that picks the new block: a search box and a grid with the icon of every block that matches, like the creative inventory.
	 * The grid is drawn by the panel itself (see {@link #mouseClicked}), so typing never rebuilds the widgets and the search box keeps the focus.
	 */
	private void initChoosing(Host host, int x, int y) {
		EditBox search = box(host, x + PAD, y + 40 - 22, WIDTH - 2 * PAD, filter);
		search.setHint(Component.translatable("holoprint.replace.search"));
		search.setResponder(text -> {
			filter = text.toLowerCase(Locale.ROOT).trim();
			scroll = 0;
			applyFilter();
		});
		host.focus(search);
		gridX = x + PAD;
		gridY = y + 40;
		gridWidth = WIDTH - 2 * PAD;
		columns = Math.max(1, (gridWidth - SCROLLBAR - 2) / CELL);
		gridRows = Math.max(1, (backY(y) - 6 - gridY) / CELL);
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		button(host, x + PAD, backY(y), 100, Component.translatable("gui.back"), () -> {
			choosing = false;
			scroll = listScroll;
			host.rebuild();
		});
	}

	/** The rows of the grid that do not fit: how far it can scroll. */
	private int maxScroll() {
		return Math.max(0, (choices.size() + columns - 1) / columns - gridRows);
	}

	/** The block under the pointer in the grid, or null. */
	private Block blockAt(double mouseX, double mouseY) {
		int column = (int) Math.floor((mouseX - gridX) / CELL);
		int row = (int) Math.floor((mouseY - gridY) / CELL);
		if (mouseX < gridX || mouseY < gridY || column >= columns || row >= gridRows) {
			return null;
		}
		int index = (scroll + row) * columns + column;
		return index >= 0 && index < choices.size() ? choices.get(index) : null;
	}

	private int totalRows() {
		return (choices.size() + columns - 1) / columns;
	}

	private int thumbHeight() {
		return Math.max(10, gridRows * CELL * gridRows / Math.max(1, totalRows()));
	}

	/** Whether the pointer is on the scrollbar of the grid, a little wider than it is drawn so it is easy to grab. */
	private boolean overBar(double x, double y) {
		int barX = gridX + columns * CELL + 1;
		return totalRows() > gridRows && x >= barX - 2 && x < barX + SCROLLBAR + 3 && y >= gridY && y < gridY + gridRows * CELL;
	}

	/** Scrolls the grid so the middle of the scrollbar's thumb is at {@code y}. */
	private void dragTo(double y) {
		int travel = Math.max(1, gridRows * CELL - thumbHeight());
		double fraction = (y - gridY - thumbHeight() / 2.0) / travel;
		scroll = Math.max(0, Math.min(maxScroll(), (int) Math.round(fraction * (totalRows() - gridRows))));
	}

	@Override
	public boolean mouseDragged(Host host, double x, double y) {
		if (!dragging) {
			return false;
		}
		dragTo(y);
		return true;
	}

	@Override
	public boolean mouseReleased(Host host) {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	@Override
	public boolean mouseClicked(Host host, double x, double y, int button) {
		if (!choosing || hologram == null || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (overBar(x, y)) {
			dragging = true;
			dragTo(y);
			return true;
		}
		Block block = blockAt(x, y);
		if (block == null) {
			return false;
		}
		destination = block;
		choosing = false;
		scroll = listScroll;
		status = Component.empty();
		host.rebuild();
		return true;
	}

	/** Makes every block of the selected kind {@code with}. */
	private void apply(Block with, Host host) {
		if (hologram == null || selected == null || with == null) {
			return;
		}
		long changed = 0;
		BlockState target = with.defaultBlockState();
		// a fluid keeps its level (sources stay sources, the flow stays the flow); only a fluid's sources are counted
		if (keepProperties || selected instanceof net.minecraft.world.level.block.LiquidBlock) {
			changed = hologram.replaceAll(selected.defaultBlockState(), target, true);
		} else {
			// every state the block has in this hologram becomes exactly the new block's own state
			java.util.Set<BlockState> states = new java.util.LinkedHashSet<>();
			for (int cell : hologram.solidCells()) {
				BlockState state = hologram.gridState(cell);
				if (state != null && state.getBlock() == selected) {
					states.add(state);
				}
			}
			for (BlockState state : states) {
				changed += hologram.replaceAll(state, target, false);
			}
		}
		status = Component.translatable("holoprint.replace.done", changed);
		// the replacement is made: the kind is no longer chosen, so its highlight goes away
		selected = null;
		host.rebuild();
	}

	@Override
	public boolean mouseScrolled(Host host, double wheel) {
		if (choosing) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(wheel)));
			return true;
		}
		int max = Math.max(0, kinds.size() - listRows);
		int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(wheel)));
		if (next != scroll) {
			scroll = next;
			host.rebuild();
		}
		return true;
	}

	private void renderGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		Block hovered = blockAt(mouseX, mouseY);
		graphics.fill(gridX - 1, gridY - 1, gridX + columns * CELL + 1, gridY + gridRows * CELL + 1, 0x66000000);
		for (int i = 0; i < gridRows * columns; i++) {
			int index = scroll * columns + i;
			if (index >= choices.size()) {
				break;
			}
			Block block = choices.get(index);
			int cx = gridX + (i % columns) * CELL;
			int cy = gridY + (i / columns) * CELL;
			graphics.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, block == hovered ? 0xAAFFFFFF : block == destination ? 0x88FFD166 : 0x44FFFFFF);
			MaterialsPin.icon(graphics, stackOf(block), cx + 1, cy + 1, CELL - 3);
		}
		if (choices.isEmpty()) {
			text(graphics, Component.translatable("holoprint.replace.no_match"), gridX + 2, gridY + 4, MUTED);
		}
		int rowsTotal = totalRows();
		if (rowsTotal > gridRows) {
			int barX = gridX + columns * CELL + 1;
			int barHeight = gridRows * CELL;
			int thumb = thumbHeight();
			int thumbY = gridY + (barHeight - thumb) * scroll / Math.max(1, rowsTotal - gridRows);
			graphics.fill(barX, gridY, barX + SCROLLBAR, gridY + barHeight, 0x44000000);
			graphics.fill(barX, thumbY, barX + SCROLLBAR, thumbY + thumb, dragging ? 0xFFFFFFFF : 0xCCFFFFFF);
		}
		if (hovered != null) {
			graphics.setTooltipForNextFrame(font(), nameOf(hovered), mouseX, mouseY);
		}
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		if (hologram == null) {
			text(graphics, Component.translatable("holoprint.materials.no_hologram"), x + PAD, y + 8, MUTED);
			return;
		}
		if (choosing) {
			text(graphics, Component.translatable("holoprint.replace.choosing"), x + PAD, y, GOLD);
			renderGrid(graphics, mouseX, mouseY);
			return;
		}
		text(graphics, font().plainSubstrByWidth(hologram.displayName(), WIDTH - 2 * PAD), x + PAD, y, GOLD);

		int top = y + LIST_HEAD;
		List<Block> list = new ArrayList<>(kinds.keySet());
		for (int i = 0; i < listRows && scroll + i < list.size(); i++) {
			if (list.get(scroll + i) == selected) {
				graphics.fill(x + PAD - 2, top + i * LIST_ROW - 1, x + WIDTH - PAD + 2, top + i * LIST_ROW + LIST_ROW, CHOSEN);
			}
			ItemStack stack = stackOf(list.get(scroll + i));
			if (!stack.isEmpty()) {
				MaterialsPin.icon(graphics, stack, x + PAD, top + i * LIST_ROW + 2, ICON);
			}
		}
		int footerY = top + listRows * LIST_ROW + 4;
		int madeY = footerY + 26;
		List<Hologram.Replacement> made = hologram.replacements();
		text(graphics, made.isEmpty() ? Component.translatable("holoprint.replace.none") : status.getString().isEmpty() ? Component.translatable("holoprint.replace.made", made.size()) : status, x + PAD, madeY, made.isEmpty() ? MUTED : GOLD);
		for (int i = 0; i < MADE_ROWS && i < made.size(); i++) {
			Hologram.Replacement replacement = made.get(made.size() - 1 - i);
			Component line = Component.translatable(replacement.anyState() ? "holoprint.replace.line_kind" : "holoprint.replace.line_state",
				replacement.from().getBlock().getName(), replacement.to().isAir() ? Component.translatable("holoprint.replace.air_name") : replacement.to().getBlock().getName());
			text(graphics, font().plainSubstrByWidth(line.getString(), WIDTH - 2 * PAD - 24), x + PAD, madeY + 14 + i * LIST_ROW + 4, TEXT);
		}
		if (made.size() > MADE_ROWS) {
			text(graphics, Component.translatable("holoprint.materials.more", made.size() - MADE_ROWS), x + WIDTH - 150, madeY, MUTED);
		}
		if (made.isEmpty() && !status.getString().isEmpty()) {
			text(graphics, status, x + PAD, madeY + 14, MUTED);
		}
	}
}
