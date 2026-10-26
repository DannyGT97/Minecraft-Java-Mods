package io.github.dannygt97.holoprint.ui;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.hologram.LayerView;
import io.github.dannygt97.holoprint.placement.Editor;
import io.github.dannygt97.holoprint.placement.EditorKeys;
import io.github.dannygt97.holoprint.placement.EditorMode;
import io.github.dannygt97.holoprint.placement.HologramBuilder;
import io.github.dannygt97.holoprint.placement.LayerControl;
import io.github.dannygt97.holoprint.ui.panel.AreaEditorPanel;
import io.github.dannygt97.holoprint.ui.panel.ConfigPanel;
import io.github.dannygt97.holoprint.ui.panel.EditorPanel;
import io.github.dannygt97.holoprint.ui.panel.LoadPanel;
import io.github.dannygt97.holoprint.ui.panel.MaterialsPanel;
import io.github.dannygt97.holoprint.ui.panel.PlacementsPanel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The free camera with a visible cursor. It is not a menu: it is transparent, does not pause the
 * game and keeps the camera, the hologram and the editing mode exactly as they were. It only frees
 * the mouse so the buttons can be pressed; the key that opened it closes it again and the camera
 * goes back to being driven by the mouse. Movement keys are read straight from the keyboard while
 * it is open, because the game stops updating them when a screen is showing. The windows opened from
 * the toolbar are panels drawn inside this one screen.
 */
public final class EditorScreen extends Screen implements EditorPanel.Host {
	private static final int BUTTON = 18;
	private static final int GAP = 3;
	private static final int GROUP_GAP = 8;
	private static final int MARGIN = 6;
	private static final int TITLE_HEIGHT = 26;
	private static final int PANEL_SHADE = 0x8A000000;

	/** The windows that are open, the last one on top; each "Back" returns to the one below. */
	private final ArrayDeque<EditorPanel> panels = new ArrayDeque<>();
	/**
	 * The key that opens this screen is usually still held when it appears, and holding it makes the
	 * keyboard repeat it; without this it would close the screen again at once. It only counts once it
	 * has been seen released.
	 */
	private boolean toggleKeyReleased;

	public EditorScreen() {
		super(Component.translatable("holoprint.editor.title"));
	}

	/** Registers the buttons that exist so far. Called once at start-up. */
	public static void registerTools() {
		for (EditorMode mode : EditorMode.values()) {
			Toolbar.register(new Toolbar.Tool(0, mode.glyph(), mode.label(), () -> Editor.setMode(Minecraft.getInstance(), mode),
				() -> Editor.mode() == mode, () -> mode != EditorMode.MOVE || HologramRenderer.active() != null));
		}
		// Actions: build the placement, and the layer view (the same as their keys).
		Toolbar.register(new Toolbar.Tool(1, "Bu", Component.translatable("holoprint.tool.build"),
			() -> HologramBuilder.request(Minecraft.getInstance(), null), () -> false, () -> HologramRenderer.active() != null));
		Toolbar.register(new Toolbar.Tool(1, "Ly", Component.translatable("holoprint.tool.layer_mode"),
			() -> LayerControl.toggleMode(Minecraft.getInstance()), () -> LayerView.mode() == LayerView.Mode.SINGLE, () -> !HologramRenderer.all().isEmpty()));
		Toolbar.register(new Toolbar.Tool(1, "Lup", Component.translatable("holoprint.tool.layer_up"),
			() -> LayerControl.step(Minecraft.getInstance(), 1), () -> false, () -> LayerView.mode() == LayerView.Mode.SINGLE));
		Toolbar.register(new Toolbar.Tool(1, "Ldn", Component.translatable("holoprint.tool.layer_down"),
			() -> LayerControl.step(Minecraft.getInstance(), -1), () -> false, () -> LayerView.mode() == LayerView.Mode.SINGLE));
		panelTool(2, "Ld", "holoprint.tool.load", LoadPanel.class, LoadPanel::new);
		panelTool(2, "Pl", "holoprint.tool.placements", PlacementsPanel.class, PlacementsPanel::new);
		panelTool(2, "Ma", "holoprint.tool.materials", io.github.dannygt97.holoprint.ui.panel.MaterialsPanel.class, io.github.dannygt97.holoprint.ui.panel.MaterialsPanel::new);
		panelTool(2, "Ar", "holoprint.tool.area_editor", AreaEditorPanel.class, () -> {
			// The area being edited is the Select mode's selection, so that mode is the one to be in.
			Editor.setMode(Minecraft.getInstance(), EditorMode.SELECT);
			return new AreaEditorPanel();
		});
		panelTool(2, "Cf", "holoprint.tool.config", ConfigPanel.class, ConfigPanel::new);
	}

	/** A toolbar button that opens a window, and closes it when pressed again. */
	private static void panelTool(int group, String glyph, String nameKey, Class<? extends EditorPanel> type, Supplier<EditorPanel> factory) {
		Toolbar.register(new Toolbar.Tool(group, glyph, Component.translatable(nameKey), () -> {
			if (Minecraft.getInstance().gui.screen() instanceof EditorScreen editor) {
				EditorPanel top = editor.panels.peek();
				boolean same = top != null && type.isInstance(top);
				editor.panels.clear();
				if (!same) {
					editor.panels.push(factory.get());
				}
				editor.rebuildWidgets();
			}
		}, () -> Minecraft.getInstance().gui.screen() instanceof EditorScreen editor && editor.panels.peek() != null && type.isInstance(editor.panels.peek()),
			() -> true));
	}

	/** Opens the material list of the active placement, or closes it when it is the window that is open. */
	public void toggleMaterials() {
		boolean open = panels.peek() instanceof MaterialsPanel;
		panels.clear();
		if (!open) {
			panels.push(new MaterialsPanel());
		}
		leaveIfBare();
	}

	/** True while a window is open over the editor. */
	public boolean hasPanel() {
		return !panels.isEmpty();
	}

	/** Opens the editor over the free camera. */
	public static void open(Minecraft mc) {
		mc.setScreenAndShow(new EditorScreen());
	}

	// --- EditorPanel.Host ---

	@Override
	public <T extends AbstractWidget> T add(T widget) {
		return addRenderableWidget(widget);
	}

	@Override
	public void open(EditorPanel panel) {
		panels.push(panel);
		rebuildWidgets();
	}

	@Override
	public void back() {
		if (!panels.isEmpty()) {
			panels.pop();
		}
		// Closing the last window shows the toolbar again (or, with the tools on the hotbar, goes back to the camera).
		leaveIfBare();
	}

	@Override
	public void closeAll() {
		panels.clear();
		leaveIfBare();
	}

	/** With the tools on the hotbar this screen is only a place for windows: with none open it goes back to the camera. */
	private void leaveIfBare() {
		if (panels.isEmpty() && HotbarTools.enabled()) {
			leave();
		} else {
			rebuildWidgets();
		}
	}

	@Override
	public void focus(AbstractWidget widget) {
		setInitialFocus(widget);
	}

	@Override
	public void rebuild() {
		rebuildWidgets();
	}

	private int panelX(EditorPanel panel) {
		return Math.max(6, (width - panel.width()) / 2);
	}

	private int panelY(EditorPanel panel) {
		return Math.max(6, (height - panel.height()) / 2);
	}

	@Override
	protected void init() {
		// While a window is open the bar is hidden, as if Tab had been pressed, so the window can use the whole screen.
		List<Toolbar.Tool> tools = panels.isEmpty() && !HotbarTools.enabled() ? Toolbar.tools() : List.<Toolbar.Tool>of();
		// One single column; on a very short screen the buttons shrink a little instead of starting a second column.
		int groups = (int) tools.stream().map(Toolbar.Tool::group).distinct().count();
		int needed = tools.size() * BUTTON + (tools.size() - 1) * GAP + Math.max(0, groups - 1) * (GROUP_GAP - GAP);
		int size = needed <= height - 2 * MARGIN || tools.isEmpty() ? BUTTON
			: Math.max(12, BUTTON * (height - 2 * MARGIN) / needed);
		int x = width - MARGIN - size;
		int y = MARGIN;
		int group = tools.isEmpty() ? 0 : tools.get(0).group();
		for (Toolbar.Tool tool : tools) {
			if (tool.group() != group) {
				y += GROUP_GAP - GAP;
				group = tool.group();
			}
			ToolButton button = addRenderableWidget(new ToolButton(x, y, size, tool));
			button.setTooltip(Tooltip.create(tool.name()));
			y += size + GAP;
		}
		EditorPanel panel = panels.peek();
		if (panel != null) {
			int px = panelX(panel);
			int py = panelY(panel);
			addRenderableWidget(Button.builder(Component.literal("x"), b -> back()).bounds(px + panel.width() - 22, py + 5, 16, 16).build());
			panel.init(this, px, py + TITLE_HEIGHT);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** True so that the controller's back button (Controlify calls {@link #onClose} for it, as the game does for Esc) closes the window. */
	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	/** Nothing behind the editor: the world stays fully visible. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

	@Override
	public void tick() {
		// Re-reads the real keyboard so that fly, up and down keep working with the cursor free,
		// except while something is being typed.
		if (!(getFocused() instanceof EditBox)) {
			KeyMapping.setAll();
		}
		if (!EditorKeys.INTERFACE.isDown()) {
			toggleKeyReleased = true;
		}
		EditorPanel open = panels.peek();
		if (open != null) {
			open.tick(this);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		EditorPanel panel = panels.peek();
		if (panel != null) {
			int px = panelX(panel);
			int py = panelY(panel);
			graphics.fill(px, py, px + panel.width(), py + panel.height(), PANEL_SHADE);
			graphics.fill(px, py + TITLE_HEIGHT - 2, px + panel.width(), py + TITLE_HEIGHT - 1, 0x40FFFFFF);
			graphics.text(font, panel.title(), px + 8, py + 9, 0xFFFFD166, true);
			panel.render(graphics, px, py + TITLE_HEIGHT, mouseX, mouseY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		EditorPanel openPanel = panels.peek();
		if (openPanel != null && openPanel.keyPressed(this, event)) {
			return true;
		}
		boolean typing = getFocused() instanceof EditBox;
		if (event.key() == InputConstants.KEY_ESCAPE) {
			if (typing) {
				setFocused(null);
			} else {
				onClose();
			}
			return true;
		}
		if (!typing && EditorKeys.MATERIALS.matches(event)) {
			toggleMaterials();
			return true;
		}
		if (!typing && EditorKeys.INTERFACE.matches(event)) {
			if (toggleKeyReleased) {
				leave();
			}
			return true;
		}
		if (super.keyPressed(event) || typing) {
			return true;
		}
		// The game does not feed key mappings while a screen is open, so the editor's own keys
		// (rotate, mirror, nudge, mode, cut...) are clicked in by hand and picked up on the next tick.
		// While a window is open the keys belong to it, so typing a name never switches mode or leaves the editor.
		if (panels.isEmpty()) {
			KeyMapping.click(InputConstants.getKey(event));
		}
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Whether the click is on a button or a window is decided before it is handled: a button can open
		// or close a window, which changes what is under the pointer, and the same click must not then
		// fall through to the world (it would, for instance, remove the selection).
		boolean overUi = getChildAt(event.x(), event.y()).isPresent() || inPanel(event.x(), event.y());
		if (!super.mouseClicked(event, doubleClick)) {
			setFocused(null);
		}
		if (overUi) {
			return true;
		}
		// This version numbers the mouse buttons from 1 (left), 2 (middle) and 3 (right).
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			Editor.onPrimaryClick();
		} else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			Editor.onSecondaryClick();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (super.mouseScrolled(x, y, scrollX, scrollY)) {
			return true;
		}
		EditorPanel panel = panels.peek();
		if (panel != null && inPanel(x, y)) {
			return panel.mouseScrolled(this, scrollY);
		}
		Freecam.changeSpeed(scrollY);
		return true;
	}

	/** What Esc and the controller's back button do: close the window on top, or with none open, leave the screen. */
	@Override
	public void onClose() {
		if (!panels.isEmpty()) {
			back();
		} else {
			leave();
		}
	}

	/** Goes back to the camera, whatever window is open. */
	private void leave() {
		minecraft.setScreenAndShow(null);
	}

	private boolean inPanel(double x, double y) {
		EditorPanel panel = panels.peek();
		if (panel == null) {
			return false;
		}
		int px = panelX(panel);
		int py = panelY(panel);
		return x >= px && x < px + panel.width() && y >= py && y < py + panel.height();
	}

	private double pointerX() {
		return minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
	}

	private double pointerY() {
		return minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
	}

	/** True when the pointer is on a button or on a window, so the world must not react to it. */
	public boolean isPointerOverUi() {
		double x = pointerX();
		double y = pointerY();
		return getChildAt(x, y).isPresent() || inPanel(x, y);
	}

	/** Direction of the ray from the camera through the pointer, or null while the pointer is over the interface. */
	public Vec3 pointerRay() {
		if (isPointerOverUi()) {
			return null;
		}
		double ndcX = pointerX() / width * 2.0 - 1.0;
		double ndcY = 1.0 - pointerY() / height * 2.0;
		double fov = minecraft.gameRenderer.mainCamera().getFov();
		return Freecam.rayThrough(ndcX, ndcY, fov, (double) width / height);
	}

	/** Square button of the toolbar: the game's button background with a small picture on it. */
	private static final class ToolButton extends Button {
		private final Toolbar.Tool tool;

		ToolButton(int x, int y, int size, Toolbar.Tool tool) {
			super(x, y, size, size, tool.name(), b -> tool.action().run(), DEFAULT_NARRATION);
			this.tool = tool;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			// Some buttons depend on the state of the editor (a finished selection, the clipboard).
			active = tool.enabled().getAsBoolean();
			int x = getX();
			int y = getY();
			boolean selected = tool.selected().getAsBoolean();
			// The game's own button background, so the toolbar looks like the buttons of the settings screens.
			extractDefaultSprite(graphics);
			// A dark plate inside the frame, so the picture keeps the contrast it had on the flat buttons.
			graphics.fill(x + 2, y + 2, x + getWidth() - 2, y + getHeight() - 2, active ? 0xAA000000 : 0x66000000);
			if (selected) {
				graphics.fill(x, y, x + getWidth(), y + 1, 0xFFFFD166);
				graphics.fill(x, y + getHeight() - 1, x + getWidth(), y + getHeight(), 0xFFFFD166);
				graphics.fill(x, y, x + 1, y + getHeight(), 0xFFFFD166);
				graphics.fill(x + getWidth() - 1, y, x + getWidth(), y + getHeight(), 0xFFFFD166);
			}
			Minecraft mc = Minecraft.getInstance();
			int color = !active ? 0xFF707070 : selected ? 0xFFFFD166 : 0xFFFFFFFF;
			if (Icons.has(tool.glyph())) {
				Icons.draw(graphics, tool.glyph(), x, y, getWidth(), color);
			} else {
				graphics.text(mc.font, tool.glyph(), x + (getWidth() - mc.font.width(tool.glyph())) / 2 + 1, y + (getHeight() - 9) / 2 + 1, color, true);
			}
		}
	}
}
