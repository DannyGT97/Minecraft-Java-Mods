package io.github.dannygt97.holoprint.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.dannygt97.holoprint.config.HoloprintConfig;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.hologram.Verifier;
import io.github.dannygt97.holoprint.placement.EditorKeys;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The material list of the active placement kept on the screen, in the top right corner: what is still missing, in the order chosen for the
 * material list window (which uses {@link Sort} too). It works in the editor and out of it; a long list is cut and says how many more there are.
 */
public final class MaterialsPin {
	/** The orders the lists can be in. */
	public enum Sort {
		MISSING_DESC("missing_desc"), MISSING_ASC("missing_asc"), NEEDED_DESC("needed_desc"), NEEDED_ASC("needed_asc"), NAME("name");

		private final String key;

		Sort(String key) {
			this.key = key;
		}

		public Component label() {
			return Component.translatable("holoprint.materials.sort." + key);
		}

		Comparator<Verifier.Row> comparator() {
			Comparator<Verifier.Row> byName = (a, b) -> a.name().getString().compareToIgnoreCase(b.name().getString());
			return switch (this) {
				case MISSING_DESC -> Comparator.comparingLong(Verifier.Row::missing).reversed().thenComparing(byName);
				case MISSING_ASC -> Comparator.comparingLong(Verifier.Row::missing).thenComparing(byName);
				case NEEDED_DESC -> Comparator.comparingLong(Verifier.Row::needed).reversed().thenComparing(byName);
				case NEEDED_ASC -> Comparator.comparingLong(Verifier.Row::needed).thenComparing(byName);
				case NAME -> byName;
			};
		}
	}

	private static final int ROW = 10;
	private static final int MAX_ROWS = 14;
	private static final int NAME_WIDTH = 130;
	/** The size of the item icons of the rows and the gap after them. */
	private static final int ICON = 9;
	private static final int ICON_GAP = 2;
	/** First line of text; its background starts 3 above, level with the top of the editor's legend. */
	private static final int TOP = 10;
	/** The widest the title lines may make the list. */
	private static final int HEAD_WIDTH = 220;

	private MaterialsPin() {}

	public static Sort sort() {
		Sort[] all = Sort.values();
		return all[Math.max(0, Math.min(all.length - 1, HoloprintConfig.get().materialsSort))];
	}

	public static void cycleSort() {
		HoloprintConfig config = HoloprintConfig.get();
		config.materialsSort = (sort().ordinal() + 1) % Sort.values().length;
		config.save();
	}

	public static boolean pinned() {
		return HoloprintConfig.get().materialsPinned;
	}

	public static void togglePinned() {
		HoloprintConfig config = HoloprintConfig.get();
		config.materialsPinned = !config.materialsPinned;
		config.save();
	}

	/** The rows in the chosen order. */
	public static List<Verifier.Row> sorted(List<Verifier.Row> rows) {
		List<Verifier.Row> out = new ArrayList<>(rows);
		out.sort(sort().comparator());
		return out;
	}

	/** Slots of a shulker box. */
	private static final int BOX_SLOTS = 27;

	private static long stackSize(Verifier.Row row) {
		return row.key() instanceof Item item ? Math.max(1, item.getDefaultMaxStackSize()) : 64;
	}

	/** The slots a count takes up, each holding as many as the item stacks to. */
	public static long stacks(Verifier.Row row, long count) {
		long size = stackSize(row);
		return (count + size - 1) / size;
	}

	/** A piece of a count in boxes, stacks and loose items; a box is drawn as the icon of a shulker box after its number. */
	private record Part(String text, boolean box) {}

	private static List<Part> parts(Verifier.Row row, long count) {
		long size = stackSize(row);
		long stacks = count / size;
		long boxes = stacks / BOX_SLOTS;
		long inBox = stacks % BOX_SLOTS;
		long loose = count % size;
		List<Part> parts = new ArrayList<>();
		if (boxes > 0) {
			parts.add(new Part(String.valueOf(boxes), true));
		}
		if (inBox > 0) {
			parts.add(new Part(Component.translatable("holoprint.pack.stack", inBox).getString(), false));
		}
		if (loose > 0) {
			parts.add(new Part(String.valueOf(loose), false));
		}
		return parts;
	}

	/** A count as full shulker boxes, stacks and loose items ("12 box + 5 st + 33"), left out where zero; empty for nothing. For text, the lists draw icons instead. */
	public static String pack(Verifier.Row row, long count) {
		List<String> out = new ArrayList<>();
		for (Part part : parts(row, count)) {
			out.add(part.box ? Component.translatable("holoprint.pack.box", part.text).getString() : part.text);
		}
		return String.join(" + ", out);
	}

	/** How wide {@link #drawPack} is. */
	public static int packWidth(Font font, Verifier.Row row, long count, int icon) {
		int width = 0;
		List<Part> parts = parts(row, count);
		for (int i = 0; i < parts.size(); i++) {
			width += (i > 0 ? font.width(" + ") : 0) + font.width(parts.get(i).text) + (parts.get(i).box ? ICON_GAP + icon : 0);
		}
		return width;
	}

	/** {@link #pack} as the lists show it, ending at `rightEdge`: the boxes as the icon of a shulker box (its default colour) after their number. */
	public static void drawPack(GuiGraphicsExtractor graphics, Font font, Verifier.Row row, long count, int rightEdge, int y, int color, int icon) {
		int x = rightEdge - packWidth(font, row, count, icon);
		List<Part> parts = parts(row, count);
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				graphics.text(font, " + ", x, y, color, true);
				x += font.width(" + ");
			}
			Part part = parts.get(i);
			graphics.text(font, part.text, x, y, color, true);
			x += font.width(part.text);
			if (part.box) {
				icon(graphics, box(), x + ICON_GAP, y + (9 - icon) / 2, icon);
				x += ICON_GAP + icon;
			}
		}
	}

	private static ItemStack box;
	private static final Map<Item, ItemStack> STACKS = new HashMap<>();

	/** The shulker box of the default colour (made when first needed: the items have no components yet while this class loads). */
	private static ItemStack box() {
		if (box == null) {
			box = new ItemStack(Items.SHULKER_BOX);
		}
		return box;
	}

	/** The stack that stands for a row (nothing where the row is not an item). */
	public static ItemStack stackOf(Verifier.Row row) {
		return row.key() instanceof Item item ? STACKS.computeIfAbsent(item, ItemStack::new) : ItemStack.EMPTY;
	}

	/** An item icon `size` pixels wide (it is drawn 16 wide and scaled). */
	public static void icon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int size) {
		if (stack.isEmpty()) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(size / 16F, size / 16F);
		graphics.fakeItem(stack, 0, 0);
		graphics.pose().popMatrix();
	}

	/** How many shulker boxes the whole of the rows' counts would fill, loose items counted as the stacks they take up ("35.3"). */
	public static String boxes(List<Verifier.Row> rows, boolean missing) {
		long stacks = 0;
		for (Verifier.Row row : rows) {
			stacks += stacks(row, missing ? row.pending() : row.needed());
		}
		return String.format(java.util.Locale.ROOT, "%.1f", stacks / (double) BOX_SLOTS);
	}

	/** How many of each item the player carries. */
	public static Map<Item, Integer> carried() {
		Map<Item, Integer> counts = new HashMap<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
				if (!stack.isEmpty()) {
					counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
		}
		return counts;
	}

	/** Runs every tick, in the editor and out of it. */
	public static void tick(Minecraft mc) {
		while (EditorKeys.PIN_MATERIALS.consumeClick()) {
			togglePinned();
			if (mc.player != null) {
				mc.gui.hud.setOverlayMessage(Component.translatable("holoprint.message.pin_materials", Component.translatable(pinned() ? "options.on" : "options.off")), false);
			}
		}
	}

	/** What the pinned list shows and how big it is, or null when it is not on the screen. */
	private record Layout(Hologram hologram, List<Component> head, List<Verifier.Row> rows, long more, int numberWidth, int packWidth, int width, int height) {}

	private static Layout layout(Minecraft mc, GuiGraphicsExtractor graphics) {
		HoloprintConfig config = HoloprintConfig.get();
		Hologram hologram = HologramRenderer.activeAnywhere();
		if (!config.materialsPinned || !config.showHolograms || hologram == null || mc.player == null || mc.gui.screen() != null) {
			return null;
		}
		Verifier.request(hologram);
		Verifier.Result result = hologram.verification();
		List<Component> head = new ArrayList<>();
		head.add(Component.translatable("holoprint.hud.pinned.head", hologram.displayName()));
		List<Verifier.Row> rows = new ArrayList<>();
		long more = 0;
		if (result == null || !result.matches(hologram.origin(), hologram.quarterTurns(), hologram.mirrored())) {
			head.add(Component.translatable("holoprint.hud.pinned.checking"));
		} else {
			for (Verifier.Row row : sorted(result.rows)) {
				if (row.pending() > 0) {
					rows.add(row);
				}
			}
			if (rows.isEmpty()) {
				head.add(Component.translatable("holoprint.hud.pinned.done"));
			} else {
				head.add(Component.translatable("holoprint.hud.pinned.boxes", boxes(rows, true)));
			}
			int limit = Math.max(3, Math.min(MAX_ROWS, (HudText.height(graphics) - TOP - 60) / ROW));
			if (rows.size() > limit) {
				more = rows.size() - limit;
				rows = rows.subList(0, limit);
			}
		}
		int numberWidth = 0;
		int packWidth = 0;
		for (Verifier.Row row : rows) {
			numberWidth = Math.max(numberWidth, mc.font.width(String.valueOf(row.pending())));
			packWidth = Math.max(packWidth, packWidth(mc.font, row, row.pending(), ICON));
		}
		int width = 0;
		for (Verifier.Row row : rows) {
			width = Math.max(width, ICON + ICON_GAP + Math.min(NAME_WIDTH, mc.font.width(row.name())) + 10 + packWidth + 10 + numberWidth);
		}
		// The title lines (the placement's name) may make the list wider than the rows, up to a limit.
		for (Component line : head) {
			width = Math.max(width, Math.min(HEAD_WIDTH, mc.font.width(line)));
		}
		int height = (head.size() + rows.size() + (more > 0 ? 1 : 0)) * ROW + 4;
		return new Layout(hologram, head, rows, more, numberWidth, packWidth, width, height);
	}

	/** Where the pinned list starts, in the game's GUI units, or the width of the screen when it is not shown; the legend keeps to the left of it. */
	public static int leftEdge(GuiGraphicsExtractor graphics) {
		Layout layout = layout(Minecraft.getInstance(), graphics);
		if (layout == null) {
			return graphics.guiWidth();
		}
		return Math.round((HudText.width(graphics) - layout.width() - 8 - 4) * HudText.SCALE);
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		Layout layout = layout(mc, graphics);
		if (layout == null) {
			return;
		}
		List<Component> head = layout.head();
		List<Verifier.Row> rows = layout.rows();
		long more = layout.more();
		int numberWidth = layout.numberWidth(), width = layout.width(), height = layout.height();
		Map<Item, Integer> carried = carried();
		boolean creative = mc.player.hasInfiniteMaterials();
		HudText.begin(graphics);
		int x = HudText.width(graphics) - width - 8;
		int y = TOP;
		graphics.fill(x - 4, y - 3, x + width + 4, y - 3 + height + 2, 0x88000000);
		for (int i = 0; i < head.size(); i++) {
			graphics.text(mc.font, mc.font.plainSubstrByWidth(head.get(i).getString(), width), x, y, i == 0 ? 0xFFFFD166 : 0xFFB0B0B0, true);
			y += ROW;
		}
		for (Verifier.Row row : rows) {
			int have = row.key() instanceof Item item ? carried.getOrDefault(item, 0) : 0;
			boolean enough = creative || have >= row.pending();
			icon(graphics, stackOf(row), x, y, ICON);
			graphics.text(mc.font, mc.font.plainSubstrByWidth(row.name().getString(), NAME_WIDTH), x + ICON + ICON_GAP, y, 0xFFE0E0E0, true);
			String count = String.valueOf(row.pending());
			graphics.text(mc.font, count, x + width - mc.font.width(count), y, enough ? 0xFF70D070 : 0xFFFFA040, true);
			drawPack(graphics, mc.font, row, row.pending(), x + width - numberWidth - 10, y, 0xFF909090, ICON);
			y += ROW;
		}
		if (more > 0) {
			graphics.text(mc.font, Component.translatable("holoprint.hud.pinned.more", more), x, y, 0xFF909090, true);
		}
		HudText.end(graphics);
	}
}
