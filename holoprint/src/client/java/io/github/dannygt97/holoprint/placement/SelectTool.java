package io.github.dannygt97.holoprint.placement;

import java.util.List;

import io.github.dannygt97.holoprint.selection.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Select-area mode: a click marks the first corner, the box grows with the pointer, a second click
 * fixes it, and one more click removes it again. The finished area can be copied, cut, deleted or
 * saved as a schematic. The selection belongs to this mode: it disappears when the mode is left.
 */
final class SelectTool implements EditorTool {
	@Override
	public void enter(Minecraft mc, boolean fresh) {}

	@Override
	public void leave(Minecraft mc) {
		Selection.clear();
	}

	@Override
	public void tick(Minecraft mc, Vec3 ray) {
		if (ray != null && !Selection.isEmpty() && !Selection.isComplete()) {
			Selection.preview(Editor.targetBlock(mc, ray));
		}
	}

	@Override
	public void primaryClick(Minecraft mc, Vec3 ray) {
		if (ray == null) {
			return;
		}
		if (Selection.isComplete()) {
			// A click on a finished selection only removes it; the next click starts a new one.
			Selection.clear();
			return;
		}
		BlockPos corner = Editor.targetBlock(mc, ray);
		if (Selection.isEmpty()) {
			Selection.begin(corner);
		} else {
			Selection.finish(corner);
		}
	}

	/** Right click clears the selection; with nothing selected it leaves the editor. */
	@Override
	public void secondaryClick(Minecraft mc, Vec3 ray) {
		if (Selection.isEmpty()) {
			Editor.stop(mc);
		} else {
			Selection.clear();
		}
	}

	@Override
	public void cancel(Minecraft mc) {
		Selection.clear();
	}

	@Override
	public boolean escape(Minecraft mc) {
		if (Selection.isEmpty()) {
			return false;
		}
		Selection.clear();
		return true;
	}

	@Override
	public List<Component> legend() {
		Component first;
		if (Selection.isEmpty()) {
			first = Component.translatable("holoprint.hud.select.start");
		} else if (!Selection.isComplete()) {
			first = Component.translatable("holoprint.hud.select.second");
		} else {
			first = Component.translatable("holoprint.hud.select.done");
		}
		if (Selection.isEmpty()) {
			return List.of(first);
		}
		Component size = Component.translatable("holoprint.hud.select.size", Selection.sizeX(), Selection.sizeY(), Selection.sizeZ(), Selection.volume());
		if (!Selection.isComplete()) {
			return List.of(first, size);
		}
		Component copy = EditorHud.ctrl(EditorKeys.COPY);
		Component actions = SelectionActions.canChangeWorld(Minecraft.getInstance())
			? Component.translatable("holoprint.hud.select.actions", copy, EditorHud.ctrl(EditorKeys.CUT),
				EditorHud.name(EditorKeys.DELETE), EditorHud.name(EditorKeys.SAVE))
			: Component.translatable("holoprint.hud.select.actions_survival", copy, EditorHud.name(EditorKeys.SAVE));
		return List.of(first, size, actions,
			Component.translatable("holoprint.hud.select.clear", EditorHud.name(EditorKeys.CANCEL)));
	}
}
