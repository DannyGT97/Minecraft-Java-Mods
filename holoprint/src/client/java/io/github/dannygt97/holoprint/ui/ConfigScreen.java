package io.github.dannygt97.holoprint.ui;

import io.github.dannygt97.holoprint.ui.panel.ConfigPanel;
import io.github.dannygt97.holoprint.ui.panel.EditorPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * The mod's settings as a screen of their own, for the Mod Menu button: the very same window the editor opens with its "Cf" button,
 * shown over the screen it was opened from.
 */
public final class ConfigScreen extends Screen implements EditorPanel.Host {
	private static final int TITLE_HEIGHT = 26;
	private static final int PANEL_SHADE = 0xC0000000;

	private final Screen parent;
	private final ConfigPanel panel = new ConfigPanel();

	public ConfigScreen(Screen parent) {
		super(Component.translatable("holoprint.panel.config"));
		this.parent = parent;
	}

	private int panelX() {
		return Math.max(6, (width - panel.width()) / 2);
	}

	private int panelY() {
		return Math.max(6, (height - panel.height()) / 2);
	}

	@Override
	protected void init() {
		panel.init(this, panelX(), panelY() + TITLE_HEIGHT);
	}

	/** The window is drawn with the background, so it lies under the buttons the panel adds. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int px = panelX();
		int py = panelY();
		graphics.fill(px, py, px + panel.width(), py + panel.height(), PANEL_SHADE);
		graphics.fill(px, py + TITLE_HEIGHT - 2, px + panel.width(), py + TITLE_HEIGHT - 1, 0x40FFFFFF);
		graphics.text(font, panel.title(), px + 8, py + 9, 0xFFFFD166, true);
		panel.render(graphics, px, py + TITLE_HEIGHT, mouseX, mouseY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return panel.keyPressed(this, event) || super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (super.mouseScrolled(x, y, scrollX, scrollY)) {
			return true;
		}
		return panel.mouseScrolled(this, scrollY);
	}

	@Override
	public void tick() {
		panel.tick(this);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	// --- EditorPanel.Host ---

	@Override
	public <T extends AbstractWidget> T add(T widget) {
		return addRenderableWidget(widget);
	}

	@Override
	public void open(EditorPanel other) {
		// The settings window opens no other window.
	}

	@Override
	public void back() {
		onClose();
	}

	@Override
	public void closeAll() {
		onClose();
	}

	@Override
	public void focus(AbstractWidget widget) {
		setInitialFocus(widget);
	}

	@Override
	public void rebuild() {
		rebuildWidgets();
	}
}
