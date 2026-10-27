package io.github.dannygt97.holoprint.ui.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A window inside the editor screen (placements, area editor, configuration...). Panels are not
 * game screens: they live inside the editor's own screen, so opening and closing one never disturbs
 * the camera, the hologram or the editing mode.
 */
public abstract class EditorPanel {
	/** What a panel needs from the screen that shows it. */
	public interface Host {
		<T extends AbstractWidget> T add(T widget);

		/** Shows another panel on top of this one; {@link #back()} returns here. */
		void open(EditorPanel panel);

		/** Closes this panel and returns to the one below it, or to the bare editor. */
		void back();

		/** Closes every window and returns to the free camera, with the toolbar hidden as if Tab had been pressed. */
		void closeAll();

		/** Gives the keyboard focus to a text box, so that typing goes into it. */
		void focus(AbstractWidget widget);

		/** Builds the widgets of the current panel again (after a category or a list changed). */
		void rebuild();
	}

	protected static final int PAD = 8;
	protected static final int ROW = 22;
	protected static final int TEXT = 0xFFE0E0E0;
	protected static final int MUTED = 0xFF909090;
	protected static final int GOLD = 0xFFFFD166;

	public abstract Component title();

	public abstract int width();

	public abstract int height();

	/** Adds the panel's widgets. {@code x} and {@code y} are the top-left corner of the content, below the title. */
	public abstract void init(Host host, int x, int y);

	/** A key press, offered to the open window before the editor uses it; true when the window took it (for instance to rebind a key). */
	public boolean keyPressed(Host host, net.minecraft.client.input.KeyEvent event) {
		return false;
	}

	/** The mouse wheel over the panel; true when the panel used it. */
	public boolean mouseScrolled(Host host, double scroll) {
		return false;
	}

	/** A click inside the window that landed on no widget (it may be on something the panel draws itself); true when the panel took it. */
	public boolean mouseClicked(Host host, double x, double y, int button) {
		return false;
	}

	/** The pointer moved with the left button held, after a click this window took; true when the window used it. */
	public boolean mouseDragged(Host host, double x, double y) {
		return false;
	}

	/** The mouse button was released; true when the window had been dragging something. */
	public boolean mouseReleased(Host host) {
		return false;
	}

	/** Called every tick while the window is open. */
	public void tick(Host host) {}

	/** Draws what is not a widget (labels, lists). Called before the widgets are drawn. */
	public void render(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {}

	/** Width a window may use: the whole screen minus a margin (the toolbar is hidden while a window is open). */
	protected static int availableWidth() {
		return Minecraft.getInstance().getWindow().getGuiScaledWidth() - 12;
	}

	/** Height of the screen in GUI units, minus a small margin: a window must fit in it. */
	protected static int available() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight() - 12;
	}

	/** How much a form that needs {@code nominalHeight} must shrink vertically to fit the screen. */
	protected static float fit(int nominalHeight) {
		return Math.max(0.78F, Math.min(1.0F, (float) (available() - 26) / nominalHeight));
	}

	protected static Font font() {
		return Minecraft.getInstance().font;
	}

	protected static Button button(Host host, int x, int y, int width, Component label, Runnable action) {
		return host.add(Button.builder(label, b -> action.run()).bounds(x, y, width, 20).build());
	}

	protected static Button small(Host host, int x, int y, int width, String label, Runnable action) {
		return host.add(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, width, 16).build());
	}

	protected static EditBox box(Host host, int x, int y, int width, String value) {
		EditBox box = new EditBox(font(), x, y, width, 18, Component.empty());
		box.setValue(value);
		return host.add(box);
	}

	/** A value with minus and plus buttons around it; they do nothing yet. */
	protected static void stepper(Host host, int x, int y, Runnable minus, Runnable plus) {
		small(host, x + 14, y, 16, "-", minus);
		small(host, x + 14 + 16 + 44, y, 16, "+", plus);
	}

	protected static void text(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
		graphics.text(font(), text, x, y, color, true);
	}

	protected static void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
		graphics.text(font(), text, x, y, color, true);
	}

	/** Draws the value that sits between the buttons of {@link #stepper}. */
	protected static void stepperLabel(GuiGraphicsExtractor graphics, int x, int y, String axis, String value) {
		text(graphics, axis, x + 2, y + 4, MUTED);
		String shown = font().plainSubstrByWidth(value, 42);
		text(graphics, shown, x + 14 + 16 + 2 + (44 - font().width(shown)) / 2, y + 4, TEXT);
	}
}
