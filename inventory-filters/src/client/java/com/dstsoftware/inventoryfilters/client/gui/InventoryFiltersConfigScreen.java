package com.dstsoftware.inventoryfilters.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.dstsoftware.inventoryfilters.client.config.InventoryFiltersConfig;
import com.dstsoftware.inventoryfilters.inventory.InventorySorter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class InventoryFiltersConfigScreen extends Screen {
	private static final int ROW_HEIGHT = 24;

	private final Screen parent;
	private final List<Runnable> refreshers = new ArrayList<>();

	public InventoryFiltersConfigScreen(Screen parent) {
		super(Component.translatable("inventoryfilters.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		refreshers.clear();
		int left = width / 2 - 140;
		int top = height / 2 - 58;

		// The master switch comes first; the rest are greyed out while the mod is off.
		addButton(left, top, false, () -> label("inventoryfilters.config.enabled", InventoryFiltersConfig.isEnabled()), () ->
			InventoryFiltersConfig.setEnabled(!InventoryFiltersConfig.isEnabled()));
		addButton(left, top + ROW_HEIGHT, true, () -> Component.translatable("inventoryfilters.config.sort_mode",
			Component.translatable("inventoryfilters.sort." + InventoryFiltersConfig.modeKey(InventoryFiltersConfig.getSortMode()))), () ->
			InventoryFiltersConfig.setSortMode((InventoryFiltersConfig.getSortMode() + 1) % InventorySorter.MODE_COUNT));
		addButton(left, top + ROW_HEIGHT * 2, true, () -> label("inventoryfilters.config.reverse", InventoryFiltersConfig.isReverseOrder()), () ->
			InventoryFiltersConfig.setReverseOrder(!InventoryFiltersConfig.isReverseOrder()));
		addButton(left, top + ROW_HEIGHT * 3, true, () -> label("inventoryfilters.config.hotbar", InventoryFiltersConfig.isIncludeHotbar()), () ->
			InventoryFiltersConfig.setIncludeHotbar(!InventoryFiltersConfig.isIncludeHotbar()));

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
			.bounds(left, top + ROW_HEIGHT * 4 + 8, 280, 20).build());
		refreshAll();
	}

	private void addButton(int x, int y, boolean needsEnabled, Supplier<Component> label, Runnable onPress) {
		Button button = addRenderableWidget(Button.builder(label.get(), b -> {
			onPress.run();
			refreshAll();
		}).bounds(x, y, 280, 20).build());
		refreshers.add(() -> {
			button.setMessage(label.get());
			button.active = !needsEnabled || InventoryFiltersConfig.isEnabled();
		});
	}

	private static Component label(String key, boolean value) {
		return Component.translatable(key, Component.translatable(value ? "options.on" : "options.off"));
	}

	private void refreshAll() {
		refreshers.forEach(Runnable::run);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Mod Menu can render its own blurred background earlier in this frame. Avoid asking
		// GuiRenderState to blur a second time while the config screen is being opened.
		extractMenuBackground(graphics);
		graphics.centeredText(font, title, width / 2, height / 2 - 78, 0xFFFFFF);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
