package com.dannygt97.inventoryfilters.client.gui;

import java.util.List;

import com.dannygt97.inventoryfilters.client.config.InventoryFiltersConfig;
import com.dannygt97.inventoryfilters.network.LoadHotbarPresetPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** In-world manager for saving and loading the three local hotbar presets. */
public final class HotbarPresetsScreen extends Screen {
	private static final int SLOT_SIZE = 18;
	private static final int PREVIEW_WIDTH = 9 * SLOT_SIZE;
	private final Screen parent;
	private Button[] loadButtons;
	private ItemStack[][] previews;
	private int rowsTop;
	private int rowLeft;
	private int previewLeft;
	private int rowPitch;

	public HotbarPresetsScreen(Screen parent) {
		super(Component.translatable("inventoryfilters.preset.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		loadButtons = new Button[3];
		previews = new ItemStack[3][9];
		rowPitch = 42;
		rowsTop = Math.max(40, (height - 188) / 2);
		int rowWidth = 76 + 8 + PREVIEW_WIDTH + 12 + 14 + 5 + 14;
		rowLeft = width / 2 - rowWidth / 2;
		previewLeft = rowLeft + 84;
		ButtonTheme theme = InventoryFiltersConfig.getButtonTheme();
		Button settingsButton = addRenderableWidget(Button.builder(Component.literal("⚙"), button ->
			minecraft.setScreenAndShow(new InventoryFiltersConfigScreen(this,
				InventoryFiltersConfigScreen.HOTBAR_PRESETS_CATEGORY)))
			.bounds(width - 28, rowsTop - 30, 20, 20).build());
		settingsButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
			Component.translatable("inventoryfilters.preset.settings.tooltip")));
		for (int i = 0; i < 3; i++) {
			int preset = i;
			int y = rowsTop + i * rowPitch;
			IconButton saveButton = new IconButton(previewLeft + PREVIEW_WIDTH + 12, y + 4, 14,
				Component.translatable("inventoryfilters.preset.save.tooltip"),
				theme.presetIcon("save_preset"), theme.presetIcon("save_preset"), button -> {
				if (InventoryFiltersConfig.saveHotbarPreset(preset)) {
					if (minecraft.player != null) minecraft.player.sendOverlayMessage(
						Component.translatable("inventoryfilters.preset.saved", preset + 1));
					refreshPresets();
				}
			});
			saveButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
				Component.translatable("inventoryfilters.preset.save.tooltip")));
			addRenderableWidget(saveButton);
			IconButton loadButton = new IconButton(previewLeft + PREVIEW_WIDTH + 31, y + 4, 14,
				Component.translatable("inventoryfilters.preset.load.tooltip"),
				theme.presetIcon("load_preset"), theme.presetIcon("load_preset"), button -> loadPreset(preset));
			loadButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
				Component.translatable("inventoryfilters.preset.load.tooltip")));
			loadButtons[i] = loadButton;
			addRenderableWidget(loadButton);
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
			.bounds(width / 2 - 50, rowsTop + rowPitch * 3 + 8, 100, 20).build());
		refreshPresets();
	}

	private void refreshPresets() {
		if (loadButtons == null) return;
		for (int i = 0; i < loadButtons.length; i++) {
			List<ItemStack> stacks = InventoryFiltersConfig.getHotbarPresetStacks(i);
			boolean saved = !InventoryFiltersConfig.getHotbarPreset(i).isEmpty();
			for (int slot = 0; slot < 9; slot++) {
				previews[i][slot] = slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY;
			}
			loadButtons[i].active = saved
				&& Minecraft.getInstance().player != null && ClientPlayNetworking.canSend(LoadHotbarPresetPayload.TYPE);
		}
	}

	private void loadPreset(int preset) {
		List<String> slots = InventoryFiltersConfig.getHotbarPreset(preset);
		if (slots.size() != 9) return;
		if (!ClientPlayNetworking.canSend(LoadHotbarPresetPayload.TYPE)) {
			if (minecraft.player != null) minecraft.player.sendSystemMessage(
				Component.translatable("inventoryfilters.server_required"));
			return;
		}
		PresetLoadAnimationSuppressor.suppressNextHotbarPop(slots);
		ClientPlayNetworking.send(new LoadHotbarPresetPayload(slots, InventoryFiltersConfig.isPresetIgnoringEnchantments()));
		if (minecraft.player != null) minecraft.player.sendOverlayMessage(
			Component.translatable("inventoryfilters.preset.loading", preset + 1));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractMenuBackground(graphics);
		int center = width / 2;
		graphics.text(font, title, center - font.width(title) / 2, rowsTop - 24, 0xFFFFFFFF, true);
		for (int i = 0; i < 3; i++) {
			int y = rowsTop + i * rowPitch;
			boolean saved = !InventoryFiltersConfig.getHotbarPreset(i).isEmpty();
			graphics.text(font, Component.translatable("inventoryfilters.preset.number", i + 1),
				rowLeft, y + 3, 0xFFFFFFFF, true);
			graphics.text(font, Component.translatable(saved
				? "inventoryfilters.preset.status.saved" : "inventoryfilters.preset.status.empty"),
				rowLeft, y + 14, saved ? 0xFFAAAAAA : 0xFF777777, false);
			drawPreviewFrame(graphics, y);
			for (int slot = 0; slot < 9; slot++) {
				int x = previewLeft + slot * SLOT_SIZE;
				drawSlot(graphics, x, y + 3);
				ItemStack stack = previews[i][slot];
				if (!stack.isEmpty()) {
					graphics.item(stack, x + 1, y + 4);
					if (stack.getCount() > 1) {
						String count = Integer.toString(stack.getCount());
						graphics.text(font, count, x + 17 - font.width(count), y + 14, 0xFFFFFFFF, true);
					}
					if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y + 3 && mouseY < y + 21) {
						graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
					}
				}
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawPreviewFrame(GuiGraphicsExtractor graphics, int y) {
		int x = previewLeft - 3;
		graphics.fill(x, y, x + PREVIEW_WIDTH + 6, y + 24, 0xFF111111);
		graphics.fill(x + 1, y + 1, x + PREVIEW_WIDTH + 5, y + 23, 0xFFD0D0D0);
		graphics.fill(x + 2, y + 2, x + PREVIEW_WIDTH + 4, y + 22, 0xFF555555);
	}

	private void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF2B2B2B);
		graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF858585);
		graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + 2, 0xFFB8B8B8);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
