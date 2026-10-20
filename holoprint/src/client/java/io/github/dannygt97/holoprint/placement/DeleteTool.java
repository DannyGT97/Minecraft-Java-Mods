package io.github.dannygt97.holoprint.placement;

import java.util.List;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.selection.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Delete mode: a click on a hologram removes that placement, a click on the selected area removes the
 * selection. It only removes Holoprint's own things (holograms and the selection box); it never
 * touches the world, so it is as legal in survival as everything else.
 */
final class DeleteTool implements EditorTool {
	private static final double REACH = 256.0;

	@Override
	public void enter(Minecraft mc, boolean fresh) {}

	@Override
	public void leave(Minecraft mc) {}

	@Override
	public void tick(Minecraft mc, Vec3 ray) {}

	@Override
	public void primaryClick(Minecraft mc, Vec3 ray) {
		if (ray == null) {
			return;
		}
		Vec3 from = Freecam.eyePosition();
		Hologram hologram = HologramRenderer.pick(from, ray, REACH, null);
		if (hologram != null) {
			if (hologram.locked()) {
				Editor.message(mc, Component.translatable("holoprint.message.placement_locked"));
				return;
			}
			HologramRenderer.remove(hologram);
			Editor.message(mc, Component.translatable("holoprint.message.placement_removed"));
			return;
		}
		AABB box = Selection.box();
		if (box != null && box.clip(from, from.add(ray.scale(REACH))).isPresent()) {
			Selection.clear();
			Editor.message(mc, Component.translatable("holoprint.message.selection_removed"));
			return;
		}
		Editor.message(mc, Component.translatable("holoprint.message.nothing_there"));
	}

	@Override
	public void secondaryClick(Minecraft mc, Vec3 ray) {
		Editor.stop(mc);
	}

	@Override
	public void cancel(Minecraft mc) {}

	@Override
	public List<Component> legend() {
		return List.of(Component.translatable("holoprint.hud.delete.pick"), Component.translatable("holoprint.hud.delete.right"));
	}
}
