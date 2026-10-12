package com.dstsoftware.dynamiclights.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Settings screen with a translucent category list on the left, the options of the selected
 * category on the right, the title and version in the header and Reset/Done in the footer.
 */
public class SettingsScreen extends Screen {
	/**
	 * One button of a category; `active` greys it out (for instance while the mod is switched off)
	 * and `tooltip`, when set, is shown while hovering it.
	 */
	public record Option(Supplier<Component> label, Runnable onPress, BooleanSupplier active, @Nullable Component tooltip) {
		public Option(Supplier<Component> label, Runnable onPress, BooleanSupplier active) {
			this(label, onPress, active, null);
		}

		public static Option toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter, BooleanSupplier active) {
			return toggle(key, getter, setter, active, null);
		}

		public static Option toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter, BooleanSupplier active, @Nullable Component tooltip) {
			return new Option(
				() -> Component.translatable(key, Component.translatable(getter.getAsBoolean() ? "options.on" : "options.off")
					.withStyle(getter.getAsBoolean() ? ChatFormatting.GREEN : ChatFormatting.RED)),
				() -> setter.accept(!getter.getAsBoolean()),
				active,
				tooltip
			);
		}
	}

	public record Category(Component name, Component description, List<Option> options) {}

	private static final int HEADER_HEIGHT = 34;
	private static final int FOOTER_HEIGHT = 36;
	private static final int SIDEBAR_WIDTH = 112;
	private static final int ENTRY_HEIGHT = 22;
	private static final int OPTION_WIDTH = 322;
	private static final int OPTION_SPACING = 25;
	private static final int SIDEBAR_SHADE = 0x66000000;

	private final Screen parent;
	private final String version;
	private final List<Category> categories;
	private final Runnable reset;
	private final List<Runnable> refreshers = new ArrayList<>();
	private List<FormattedCharSequence> descriptionLines = List.of();
	private int descriptionY;
	private int selected;

	protected SettingsScreen(Screen parent, Component title, String modId, List<Category> categories, Runnable reset) {
		super(title);
		this.parent = parent;
		this.categories = categories;
		this.reset = reset;
		this.version = FabricLoader.getInstance().getModContainer(modId)
			.map(container -> "v" + container.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	@Override
	protected void init() {
		refreshers.clear();
		int bodyTop = HEADER_HEIGHT;

		int y = bodyTop + 6;
		for (int i = 0; i < categories.size(); i++) {
			int index = i;
			addRenderableWidget(new SidebarEntry(0, y, SIDEBAR_WIDTH, ENTRY_HEIGHT, categories.get(i).name(), i == selected, button -> {
				selected = index;
				rebuildWidgets();
			}));
			y += ENTRY_HEIGHT;
			if (i == selected) {
				descriptionLines = font.split(categories.get(i).description(), SIDEBAR_WIDTH - 16);
				descriptionY = y + 2;
				y += descriptionLines.size() * 10 + 8;
			}
		}

		int contentX = SIDEBAR_WIDTH;
		int contentWidth = width - SIDEBAR_WIDTH;
		int optionWidth = Math.min(OPTION_WIDTH, contentWidth - 20);
		int optionX = contentX + (contentWidth - optionWidth) / 2;
		int optionY = bodyTop + 8;
		for (Option option : categories.get(selected).options()) {
			Button button = addRenderableWidget(Button.builder(option.label().get(), b -> {
				option.onPress().run();
				refreshAll();
			}).bounds(optionX, optionY, optionWidth, 20).tooltip(option.tooltip() == null ? null : Tooltip.create(option.tooltip())).build());
			refreshers.add(() -> {
				button.setMessage(option.label().get());
				button.active = option.active().getAsBoolean();
			});
			optionY += OPTION_SPACING;
		}

		int footerButtonWidth = Math.min(156, (contentWidth - 30) / 2);
		int footerX = contentX + (contentWidth - (footerButtonWidth * 2 + 10)) / 2;
		int footerY = height - FOOTER_HEIGHT + 8;
		addRenderableWidget(Button.builder(Component.translatable("controls.reset"), b -> {
			reset.run();
			refreshAll();
		}).bounds(footerX, footerY, footerButtonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(footerX + footerButtonWidth + 10, footerY, footerButtonWidth, 20).build());

		refreshAll();
	}

	private void refreshAll() {
		refreshers.forEach(Runnable::run);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Mod Menu can render its own blurred background earlier in this frame. Avoid asking
		// GuiRenderState to blur a second time while the config screen is being opened.
		extractMenuBackground(graphics);
		graphics.fill(0, HEADER_HEIGHT, SIDEBAR_WIDTH, height - FOOTER_HEIGHT, SIDEBAR_SHADE);

		List<FormattedCharSequence> titleLines = font.split(title, SIDEBAR_WIDTH + 60);
		int titleY = (HEADER_HEIGHT - 2 - titleLines.size() * 10) / 2 + 1;
		for (FormattedCharSequence line : titleLines) {
			graphics.text(font, line, 8, titleY, 0xFFFFFFFF, true);
			titleY += 10;
		}
		graphics.text(font, version, width - 6 - font.width(version), HEADER_HEIGHT - 13, 0xFF808080, true);

		graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, 0, HEADER_HEIGHT - 2, 0.0F, 0.0F, width, 2, 32, 2);
		graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, height - FOOTER_HEIGHT, 0.0F, 0.0F, width, 2, 32, 2);

		int lineY = descriptionY;
		for (FormattedCharSequence line : descriptionLines) {
			graphics.text(font, line, 8, lineY, 0xFFA0A0A0, false);
			lineY += 10;
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	/** Flat, background-less entry of the category list. */
	private static final class SidebarEntry extends Button {
		private final boolean selected;

		SidebarEntry(int x, int y, int width, int height, Component label, boolean selected, OnPress onPress) {
			super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
			this.selected = selected;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			if (selected) {
				graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x40FFFFFF);
			} else if (isHoveredOrFocused()) {
				graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20FFFFFF);
			}
			graphics.text(Minecraft.getInstance().font, getMessage(), getX() + 8, getY() + (getHeight() - 9) / 2 + 1,
				selected ? 0xFFFFFFFF : 0xFFD0D0D0, false);
		}
	}
}
