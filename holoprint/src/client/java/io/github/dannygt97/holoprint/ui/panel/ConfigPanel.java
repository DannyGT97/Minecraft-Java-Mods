package io.github.dannygt97.holoprint.ui.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dannygt97.holoprint.config.HoloprintConfig;
import io.github.dannygt97.holoprint.placement.EditorKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * The mod's options, with the same layout as the settings of the other mods: categories on the
 * left, their options on the right, Reset and Done at the bottom. Every change is saved at once.
 * The controls category lists every key of the editor with its current key, a reset button on each
 * row, a warning when a key is shared with another action, and a search box.
 */
public final class ConfigPanel extends EditorPanel {
	private enum Category {
		GENERAL("general"), APPEARANCE("appearance"), CONTROLS("controls"), LIMITS("limits");

		final String key;

		Category(String key) {
			this.key = key;
		}
	}

	private static final int NOMINAL_WIDTH = 640;
	private static final int SIDEBAR = 104;
	private static final int KEY_ROW = 17;

	private final int WIDTH = Math.min(NOMINAL_WIDTH, availableWidth());
	private final int body = Math.max(130, Math.min(360, available() - 26 - 34));
	private final List<Consumer<GuiGraphicsExtractor>> overlays = new ArrayList<>();
	private Category selected = Category.GENERAL;
	private int keyScroll;
	private String search = "";
	private boolean searchFocused;
	/** The key whose new binding is being waited for, or null. */
	private KeyMapping listening;

	@Override
	public Component title() {
		return Component.translatable("holoprint.panel.config");
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public int height() {
		return 26 + body + 34;
	}

	private int keyRows() {
		return Math.max(3, (body - 30) / KEY_ROW);
	}

	private static List<KeyMapping> keys() {
		return List.of(EditorKeys.EDITOR, EditorKeys.MODE, EditorKeys.INTERFACE, EditorKeys.ROTATE, EditorKeys.MIRROR, EditorKeys.CANCEL,
			EditorKeys.FORWARD, EditorKeys.BACK, EditorKeys.LEFT, EditorKeys.RIGHT, EditorKeys.UP, EditorKeys.DOWN,
			EditorKeys.COPY, EditorKeys.CUT, EditorKeys.PASTE, EditorKeys.DELETE, EditorKeys.SAVE, EditorKeys.BUILD,
			EditorKeys.EASY_PLACE, EditorKeys.AUTO_FILL,
			EditorKeys.LAYER_MODE, EditorKeys.LAYER_UP, EditorKeys.LAYER_DOWN, EditorKeys.MATERIALS);
	}

	private List<KeyMapping> shownKeys() {
		String needle = search.toLowerCase(Locale.ROOT).trim();
		return keys().stream()
			.filter(k -> needle.isEmpty() || Component.translatable(k.getName()).getString().toLowerCase(Locale.ROOT).contains(needle))
			.toList();
	}

	/** The other actions (of the game or of mods) that use the same key as {@code key}. */
	private static List<KeyMapping> conflicts(KeyMapping key) {
		List<KeyMapping> found = new ArrayList<>();
		if (key.isUnbound()) {
			return found;
		}
		for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
			if (other != key && !other.isUnbound() && key.same(other) && !other.getName().startsWith("key.debug.")
					&& !other.getName().endsWith("ToolbarActivator")) {
				found.add(other);
			}
		}
		return found;
	}

	private static void rebind(KeyMapping key, InputConstants.Key bound) {
		key.setKey(bound);
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	@Override
	public boolean keyPressed(Host host, KeyEvent event) {
		if (listening == null) {
			return false;
		}
		if (event.key() != InputConstants.KEY_ESCAPE) {
			rebind(listening, InputConstants.getKey(event));
		}
		listening = null;
		host.rebuild();
		return true;
	}

	@Override
	public void init(Host host, int x, int y) {
		overlays.clear();
		int ey = y;
		for (Category category : Category.values()) {
			host.add(new SidebarEntry(x, ey, SIDEBAR, 22, Component.translatable("holoprint.config." + category.key), category == selected, () -> {
				selected = category;
				optScroll = 0;
				listening = null;
				host.rebuild();
			}));
			ey += 22;
			if (category == selected) {
				List<net.minecraft.util.FormattedCharSequence> lines = font().split(Component.translatable("holoprint.config." + category.key + ".desc"), SIDEBAR - 16);
				int lineY = ey + 2;
				overlays.add(g -> {
					int yy = lineY;
					for (net.minecraft.util.FormattedCharSequence line : lines) {
						g.text(font(), line, x + 8, yy, 0xFFA0A0A0, false);
						yy += 10;
					}
				});
				ey += lines.size() * 10 + 8;
			}
		}
		int cx = x + SIDEBAR + 12;
		int cw = WIDTH - SIDEBAR - 24;
		HoloprintConfig config = HoloprintConfig.get();
		clipTop = y;
		clipBottom = y + body;
		optScroll = Math.max(0, Math.min(optScroll, Math.max(0, contentHeight() - body)));
		int base = y - optScroll;
		switch (selected) {
			case GENERAL -> {
				toggle(host, cx, base + 4, cw, "holoprint.config.show_legend", config.showLegend, () -> config.showLegend = !config.showLegend);
				toggle(host, cx, base + 30, cw, "holoprint.config.confirm_delete", config.confirmDelete, () -> config.confirmDelete = !config.confirmDelete);
				toggle(host, cx, base + 56, cw, "holoprint.config.show_holograms", config.showHolograms, () -> config.showHolograms = !config.showHolograms);
				toggle(host, cx, base + 82, cw, "holoprint.config.show_entities", config.showEntities, () -> config.showEntities = !config.showEntities);
				toggle(host, cx, base + 108, cw, "holoprint.config.easy_place", config.easyPlace, () -> config.easyPlace = !config.easyPlace);
				toggle(host, cx, base + 134, cw, "holoprint.config.fill_held", config.fillWhileHeld, () -> config.fillWhileHeld = !config.fillWhileHeld);
				toggle(host, cx, base + 160, cw, "holoprint.config.no_support", config.placeWithoutSupport, () -> config.placeWithoutSupport = !config.placeWithoutSupport);
				toggle(host, cx, base + 186, cw, "holoprint.config.hide_built", config.hideBuilt, () -> config.hideBuilt = !config.hideBuilt);
				toggle(host, cx, base + 212, cw, "holoprint.config.show_differences", config.showDifferences, () -> config.showDifferences = !config.showDifferences);
				toggle(host, cx, base + 238, cw, "holoprint.config.show_extra", config.showExtra, () -> config.showExtra = !config.showExtra);
			}
			case APPEARANCE -> {
				if (shows(base + 4, 20)) {
					OpacitySlider opacity = new OpacitySlider(cx, base + 4, cw);
					opacity.setTooltip(tip("holoprint.config.opacity"));
					host.add(opacity);
				}
				colorRow(host, cx, base + 40, cw, "holoprint.config.hologram_color", () -> config.hologramColor, v -> config.hologramColor = v, HoloprintConfig.DEFAULT_HOLOGRAM_COLOR);
				colorRow(host, cx, base + 68, cw, "holoprint.config.selection_color", () -> config.selectionColor, v -> config.selectionColor = v, HoloprintConfig.DEFAULT_SELECTION_COLOR);
				colorRow(host, cx, base + 96, cw, "holoprint.config.flow_color", () -> config.flowColor, v -> config.flowColor = v, HoloprintConfig.DEFAULT_FLOW_COLOR);
				toggle(host, cx, base + 124, cw, "holoprint.config.biome_colors", config.biomeColors, () -> config.biomeColors = !config.biomeColors);
				toggle(host, cx, base + 150, cw, "holoprint.config.show_block_entities", config.showBlockEntities, () -> config.showBlockEntities = !config.showBlockEntities);
				toggle(host, cx, base + 176, cw, "holoprint.config.fluid_animations", config.fluidAnimations, () -> config.fluidAnimations = !config.fluidAnimations);
				toggle(host, cx, base + 202, cw, "holoprint.config.flow_arrows", config.showFlowArrows, () -> config.showFlowArrows = !config.showFlowArrows);
				toggle(host, cx, base + 228, cw, "holoprint.config.container_contents", config.showContainerContents, () -> config.showContainerContents = !config.showContainerContents);
			}
			case CONTROLS -> controls(host, cx, y, cw);
			case LIMITS -> {
				numberRow(host, cx, base + 6, cw, "holoprint.config.limit.0", () -> config.maxPlacements, v -> config.maxPlacements = v, 1, 1, 32, HoloprintConfig.DEFAULT_MAX_PLACEMENTS);
				numberRow(host, cx, base + 34, cw, "holoprint.config.limit.1", () -> config.drawDistance, v -> config.drawDistance = v, 16, 32, 512, HoloprintConfig.DEFAULT_DRAW_DISTANCE);
				numberRow(host, cx, base + 62, cw, "holoprint.config.limit.3", () -> config.blockBudget, v -> config.blockBudget = v, 10_000, 10_000, 250_000, HoloprintConfig.DEFAULT_BLOCK_BUDGET);
				numberRow(host, cx, base + 118, cw, "holoprint.config.limit.4", () -> config.fillSpeed, v -> config.fillSpeed = v, 1, 1, HoloprintConfig.MAX_FILL_SPEED, HoloprintConfig.DEFAULT_FILL_SPEED);
				numberRow(host, cx, base + 90, cw, "holoprint.config.limit.2", () -> config.copyLimit, v -> config.copyLimit = v, 500_000, 100_000, 50_000_000, HoloprintConfig.DEFAULT_COPY_LIMIT);
			}
		}
		int footerY = y + body + 4;
		button(host, x + SIDEBAR + 12, footerY, 120, Component.translatable("controls.reset"), () -> {
			if (selected == Category.CONTROLS) {
				keys().forEach(k -> k.setKey(k.getDefaultKey()));
				KeyMapping.resetMapping();
				Minecraft.getInstance().options.save();
			} else {
				resetCategory(config);
			}
			host.rebuild();
		});
		button(host, x + WIDTH - PAD - 120, footerY, 120, Component.translatable("gui.done"), host::back);
	}

	private void resetCategory(HoloprintConfig config) {
		HoloprintConfig defaults = new HoloprintConfig();
		switch (selected) {
			case GENERAL -> {
				config.showLegend = defaults.showLegend;
				config.confirmDelete = defaults.confirmDelete;
				config.showHolograms = defaults.showHolograms;
				config.showEntities = defaults.showEntities;
				config.easyPlace = defaults.easyPlace;
				config.fillWhileHeld = defaults.fillWhileHeld;
				config.placeWithoutSupport = defaults.placeWithoutSupport;
				config.hideBuilt = defaults.hideBuilt;
				config.showDifferences = defaults.showDifferences;
				config.showExtra = defaults.showExtra;
			}
			case APPEARANCE -> {
				config.hologramOpacity = defaults.hologramOpacity;
				config.fluidAnimations = defaults.fluidAnimations;
				config.showFlowArrows = defaults.showFlowArrows;
				config.showContainerContents = defaults.showContainerContents;
				config.hologramColor = defaults.hologramColor;
				config.selectionColor = defaults.selectionColor;
				config.flowColor = defaults.flowColor;
				config.biomeColors = defaults.biomeColors;
				config.showBlockEntities = defaults.showBlockEntities;
			}
			case LIMITS -> {
				config.maxPlacements = defaults.maxPlacements;
				config.drawDistance = defaults.drawDistance;
				config.blockBudget = defaults.blockBudget;
				config.fillSpeed = defaults.fillSpeed;
				config.copyLimit = defaults.copyLimit;
			}
			default -> { }
		}
		config.save();
	}

	/** The strip of the window the options of a category may use; one that falls outside it (scrolled away) is not built. */
	private static int clipTop, clipBottom;
	/** How far the options of the category are scrolled, in pixels. */
	private int optScroll;
	private static final int SCROLL_STEP = 26;

	private static boolean shows(int y, int height) {
		return y >= clipTop && y + height <= clipBottom;
	}

	/** How tall the options of the selected category are all together. */
	private int contentHeight() {
		return switch (selected) {
			case GENERAL -> 264;
			case APPEARANCE -> 254;
			case LIMITS -> 146;
			default -> 0;
		};
	}

	private static void toggle(Host host, int x, int y, int width, String key, boolean on, Runnable flip) {
		if (!shows(y, 20)) {
			return;
		}
		Component state = Component.translatable(on ? "options.on" : "options.off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
		button(host, x, y, width, Component.translatable(key, state), () -> {
			flip.run();
			HoloprintConfig.get().save();
			host.rebuild();
		}).setTooltip(tip(key));
	}

	/** The help text of the option whose label is {@code key}: it shows when the pointer rests on the option. */
	private static Tooltip tip(String key) {
		return Tooltip.create(Component.translatable(key + ".tip"));
	}

	/** An invisible area over the label of an option that is drawn as text, so the pointer on it shows the option's help. */
	private static void hover(Host host, int x, int y, int width, int height, String key) {
		HoverZone zone = new HoverZone(x, y, width, height);
		zone.setTooltip(tip(key));
		host.add(zone);
	}

	/** A colour option: a box for the hexadecimal value, a swatch with the colour and a reset button. */
	private void colorRow(Host host, int x, int y, int width, String labelKey, IntSupplier get, IntConsumer set, int fallback) {
		if (!shows(y, 18)) {
			return;
		}
		EditBox hex = box(host, x + 150, y, 72, String.format("#%06X", get.getAsInt()));
		hex.setMaxLength(7);
		hex.setTooltip(tip(labelKey));
		hover(host, x, y, 148, 18, labelKey);
		hex.setResponder(text -> {
			String digits = text.startsWith("#") ? text.substring(1) : text;
			if (digits.matches("[0-9a-fA-F]{6}")) {
				set.accept(Integer.parseInt(digits, 16));
				HoloprintConfig.get().save();
			}
		});
		small(host, x + width - 50, y + 1, 50, Component.translatable("controls.reset").getString(), () -> {
			set.accept(fallback);
			HoloprintConfig.get().save();
			host.rebuild();
		});
		overlays.add(g -> {
			text(g, Component.translatable(labelKey), x, y + 5, TEXT);
			g.fill(x + 228, y, x + 252, y + 18, 0xFFFFFFFF);
			g.fill(x + 229, y + 1, x + 251, y + 17, 0xFF000000 | get.getAsInt());
		});
	}

	/** A whole-number option with minus and plus buttons and its own reset. */
	private void numberRow(Host host, int x, int y, int width, String labelKey, IntSupplier get, IntConsumer set, int step, int min, int max, int fallback) {
		if (!shows(y, 16)) {
			return;
		}
		int resetX = x + width - 50;
		int plusX = resetX - 4 - 16;
		int minusX = plusX - 4 - 74 - 16;
		hover(host, x, y, minusX - x - 4, 16, labelKey);
		small(host, minusX, y, 16, "-", () -> {
			set.accept(Math.max(min, get.getAsInt() - (Minecraft.getInstance().hasShiftDown() ? step * 5 : step)));
			HoloprintConfig.get().save();
			host.rebuild();
		});
		small(host, plusX, y, 16, "+", () -> {
			set.accept(Math.min(max, get.getAsInt() + (Minecraft.getInstance().hasShiftDown() ? step * 5 : step)));
			HoloprintConfig.get().save();
			host.rebuild();
		});
		small(host, resetX, y, 50, Component.translatable("controls.reset").getString(), () -> {
			set.accept(fallback);
			HoloprintConfig.get().save();
			host.rebuild();
		});
		overlays.add(g -> {
			text(g, Component.translatable(labelKey), x, y + 4, TEXT);
			String value = String.format("%,d", get.getAsInt());
			text(g, value, minusX + 20 + (74 - font().width(value)) / 2, y + 4, get.getAsInt() == fallback ? TEXT : GOLD);
		});
	}

	private void controls(Host host, int cx, int y, int cw) {
		EditBox searchBox = box(host, cx, y + 2, cw, search);
		searchBox.setHint(Component.translatable("holoprint.config.search"));
		searchBox.setResponder(text -> {
			search = text;
			searchFocused = true;
			keyScroll = 0;
			host.rebuild();
		});
		if (searchFocused) {
			host.focus(searchBox);
		}
		List<KeyMapping> shown = shownKeys();
		keyScroll = Math.max(0, Math.min(keyScroll, Math.max(0, shown.size() - keyRows())));
		int keyButtonWidth = Math.min(130, cw / 3);
		for (int i = 0; i < keyRows() && keyScroll + i < shown.size(); i++) {
			KeyMapping key = shown.get(keyScroll + i);
			int rowY = y + 28 + i * KEY_ROW;
			List<KeyMapping> clashes = conflicts(key);
			Component label;
			if (listening == key) {
				label = Component.literal("> ").append(key.getTranslatedKeyMessage()).append(" <").withStyle(ChatFormatting.YELLOW);
			} else if (!clashes.isEmpty()) {
				label = Component.literal("[ ").append(key.getTranslatedKeyMessage()).append(" ]").withStyle(ChatFormatting.RED);
			} else {
				label = key.getTranslatedKeyMessage();
			}
			Button keyButton = host.add(Button.builder(label, b -> {
				listening = key;
				host.rebuild();
			}).bounds(cx + cw - keyButtonWidth - 44, rowY, keyButtonWidth, 16).build());
			if (!clashes.isEmpty()) {
				StringBuilder names = new StringBuilder();
				for (KeyMapping other : clashes) {
					names.append(names.length() == 0 ? "" : ", ").append(Component.translatable(other.getName()).getString());
				}
				keyButton.setTooltip(Tooltip.create(Component.translatable("holoprint.config.conflict", names.toString())));
			}
			Button reset = small(host, cx + cw - 40, rowY, 40, Component.translatable("controls.reset").getString(), () -> {
				rebind(key, key.getDefaultKey());
				host.rebuild();
			});
			reset.active = !key.isDefault();
			boolean clash = !clashes.isEmpty();
			overlays.add(g -> {
				String text = font().plainSubstrByWidth(Component.translatable(key.getName()).getString(), cw - keyButtonWidth - 44 - 6);
				text(g, text, cx, rowY + 4, clash ? 0xFFFF7070 : TEXT);
			});
		}
		if (shown.isEmpty()) {
			overlays.add(g -> text(g, Component.translatable("holoprint.config.no_keys"), cx, y + 34, MUTED));
		}
		if (listening != null) {
			overlays.add(g -> text(g, Component.translatable("holoprint.config.press_key"), cx, y + body - 12, GOLD));
		}
	}

	@Override
	public boolean mouseScrolled(Host host, double scroll) {
		if (selected != Category.CONTROLS) {
			int max = Math.max(0, contentHeight() - body);
			if (max == 0) {
				return false;
			}
			optScroll = Math.max(0, Math.min(max, optScroll - (int) Math.signum(scroll) * SCROLL_STEP));
			host.rebuild();
			return true;
		}
		int max = Math.max(0, shownKeys().size() - keyRows());
		keyScroll = Math.max(0, Math.min(max, keyScroll - (int) Math.signum(scroll)));
		host.rebuild();
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		// The category list runs down to the bottom of the window, behind the Reset button.
		graphics.fill(x, y, x + SIDEBAR, y + body + 34, 0x66000000);
		overlays.forEach(o -> o.accept(graphics));
		int max = selected == Category.CONTROLS ? 0 : Math.max(0, contentHeight() - body);
		if (max > 0) {
			// a slim bar on the right shows that there is more below or above
			int track = body - 4;
			int thumb = Math.max(14, track * body / contentHeight());
			int top = y + 2 + (track - thumb) * optScroll / max;
			graphics.fill(x + WIDTH - 5, y + 2, x + WIDTH - 2, y + 2 + track, 0x33FFFFFF);
			graphics.fill(x + WIDTH - 5, top, x + WIDTH - 2, top + thumb, 0x99FFFFFF);
		}
	}

	/** An area that draws nothing and does nothing but show the help of the option beside it. */
	private static final class HoverZone extends Button {
		HoverZone(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(), b -> { }, DEFAULT_NARRATION);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
	}

	/** The slider of the hologram opacity. */
	private static final class OpacitySlider extends AbstractSliderButton {
		OpacitySlider(int x, int y, int width) {
			super(x, y, width, 20, Component.empty(), (HoloprintConfig.get().hologramOpacity - 10) / 90.0);
			updateMessage();
		}

		private int opacity() {
			return 10 + (int) Math.round(value * 90);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("holoprint.config.opacity", opacity() + "%"));
		}

		@Override
		protected void applyValue() {
			HoloprintConfig.get().hologramOpacity = opacity();
			HoloprintConfig.get().save();
		}
	}

	/** Flat entry of the category list, like the ones in the other mods' settings. */
	private static final class SidebarEntry extends Button {
		private final boolean current;

		SidebarEntry(int x, int y, int width, int height, Component label, boolean current, Runnable action) {
			super(x, y, width, height, label, b -> action.run(), DEFAULT_NARRATION);
			this.current = current;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			if (current) {
				graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x40FFFFFF);
			} else if (isHoveredOrFocused()) {
				graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20FFFFFF);
			}
			graphics.text(Minecraft.getInstance().font, getMessage(), getX() + 8, getY() + (getHeight() - 9) / 2 + 1,
				current ? 0xFFFFFFFF : 0xFFD0D0D0, false);
		}
	}
}
