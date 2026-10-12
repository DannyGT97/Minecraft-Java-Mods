package com.dstsoftware.treechopper.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.dstsoftware.treechopper.tree.TreeChopperConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TreeChopperConfigScreen extends Screen {
	private static final int ROW_HEIGHT = 24;

	private final Screen parent;
	private final List<Runnable> labelRefreshers = new ArrayList<>();

	public TreeChopperConfigScreen(Screen parent) {
		super(Component.translatable("treechopper.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		labelRefreshers.clear();
		int left = width / 2 - 140;
		int top = height / 2 - 58;

		addToggle(left, top, "treechopper.config.enabled", TreeChopperConfig::isEnabled, TreeChopperConfig::setEnabled);
		addToggle(left, top + ROW_HEIGHT, "treechopper.config.no_sneaking",
			TreeChopperConfig::isAllowWithoutSneaking, TreeChopperConfig::setAllowWithoutSneaking);
		addToggle(left, top + ROW_HEIGHT * 2, "treechopper.config.nether",
			TreeChopperConfig::isNetherFellingEnabled, TreeChopperConfig::setNetherFellingEnabled);
		addToggle(left, top + ROW_HEIGHT * 3, "treechopper.config.auto_sapling",
			TreeChopperConfig::isAutoSaplingPlacement, TreeChopperConfig::setAutoSaplingPlacement);

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
			.bounds(left, top + ROW_HEIGHT * 4 + 8, 280, 20).build());
	}

	private void addToggle(int x, int y, String key, BooleanSupplier getter, Consumer<Boolean> setter) {
		Button button = addRenderableWidget(Button.builder(label(key, getter), b -> {
			setter.accept(!getter.getAsBoolean());
			refreshLabels();
		}).bounds(x, y, 280, 20).build());
		labelRefreshers.add(() -> button.setMessage(label(key, getter)));
	}

	private static Component label(String key, BooleanSupplier getter) {
		return Component.translatable(key, Component.translatable(getter.getAsBoolean() ? "options.on" : "options.off"));
	}

	private void refreshLabels() {
		labelRefreshers.forEach(Runnable::run);
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
