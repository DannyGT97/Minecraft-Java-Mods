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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
	private static final int TOP = 30;

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

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		HoloprintConfig config = HoloprintConfig.get();
		Hologram hologram = HologramRenderer.active();
		if (!config.materialsPinned || !config.showHolograms || hologram == null || mc.player == null || mc.gui.screen() != null) {
			return;
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
				if (row.missing() > 0) {
					rows.add(row);
				}
			}
			if (rows.isEmpty()) {
				head.add(Component.translatable("holoprint.hud.pinned.done"));
			}
			int limit = Math.max(3, Math.min(MAX_ROWS, (graphics.guiHeight() - TOP - 60) / ROW));
			if (rows.size() > limit) {
				more = rows.size() - limit;
				rows = rows.subList(0, limit);
			}
		}
		Map<Item, Integer> carried = carried();
		boolean creative = mc.player.hasInfiniteMaterials();
		int numberWidth = 0;
		for (Verifier.Row row : rows) {
			numberWidth = Math.max(numberWidth, mc.font.width(String.valueOf(row.missing())));
		}
		int width = 0;
		for (Component line : head) {
			width = Math.max(width, mc.font.width(line));
		}
		for (Verifier.Row row : rows) {
			width = Math.max(width, Math.min(NAME_WIDTH, mc.font.width(row.name())) + 10 + numberWidth);
		}
		width = Math.min(width, NAME_WIDTH + 10 + numberWidth);
		int height = (head.size() + rows.size() + (more > 0 ? 1 : 0)) * ROW + 4;
		int x = graphics.guiWidth() - width - 8;
		int y = TOP;
		graphics.fill(x - 4, y - 3, x + width + 4, y - 3 + height + 2, 0x88000000);
		for (int i = 0; i < head.size(); i++) {
			graphics.text(mc.font, mc.font.plainSubstrByWidth(head.get(i).getString(), width), x, y, i == 0 ? 0xFFFFD166 : 0xFFB0B0B0, true);
			y += ROW;
		}
		for (Verifier.Row row : rows) {
			int have = row.key() instanceof Item item ? carried.getOrDefault(item, 0) : 0;
			boolean enough = creative || have >= row.missing();
			graphics.text(mc.font, mc.font.plainSubstrByWidth(row.name().getString(), NAME_WIDTH), x, y, 0xFFE0E0E0, true);
			String count = String.valueOf(row.missing());
			graphics.text(mc.font, count, x + width - mc.font.width(count), y, enough ? 0xFF70D070 : 0xFFFFA040, true);
			y += ROW;
		}
		if (more > 0) {
			graphics.text(mc.font, Component.translatable("holoprint.hud.pinned.more", more), x, y, 0xFF909090, true);
		}
	}
}
