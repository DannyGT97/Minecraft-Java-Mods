package io.github.dannygt97.holoprint.placement;

import java.util.List;

import io.github.dannygt97.holoprint.config.HoloprintConfig;
import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.ui.EditorScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pointing at a chest, barrel or other container of a hologram lists what the schematic holds inside it, beside the pointer, so the
 * contents can be seen before building (the hologram itself only draws the empty chest).
 */
public final class ContainerPreview {
	private static final int SLOT = 18;
	private static final int COLUMNS = 9;
	/** How far (blocks) the pointer looks for a container: farther in the free camera. */
	private static final double REACH_PLAYER = 12.0;
	private static final double REACH_CAMERA = 128.0;
	private static final double STEP = 0.2;

	private ContainerPreview() {}

	/** The container of a hologram the pointer is on, as {@code (position, contents)}, or null. Nothing hides behind a block of a hologram or of the world. */
	public static Found aimed(Minecraft mc) {
		HoloprintConfig config = HoloprintConfig.get();
		if (!config.showContainerContents || !config.showHolograms || mc.player == null || mc.level == null) {
			return null;
		}
		boolean any = false;
		for (Hologram hologram : HologramRenderer.all()) {
			if (hologram.visible() && hologram.enabled() && hologram.hasBlockEntities()) {
				any = true;
				break;
			}
		}
		if (!any) {
			return null;
		}
		Vec3 eye, ray;
		double reach;
		if (Freecam.isActive()) {
			eye = Freecam.eyePosition();
			ray = mc.gui.screen() instanceof EditorScreen editor ? editor.pointerRay() : Freecam.lookVector();
			reach = REACH_CAMERA;
		} else if (mc.gui.screen() == null) {
			eye = mc.player.getEyePosition();
			ray = mc.player.getLookAngle();
			reach = REACH_PLAYER;
		} else {
			return null;
		}
		if (ray == null) {
			return null;
		}
		BlockPos last = null;
		for (double t = 0.0; t <= reach; t += STEP) {
			BlockPos at = BlockPos.containing(eye.add(ray.scale(t)));
			if (at.equals(last)) {
				continue;
			}
			last = at;
			for (Hologram hologram : HologramRenderer.all()) {
				if (!hologram.visible() || !hologram.enabled()) {
					continue;
				}
				BlockState state = hologram.stateAt(at);
				if (state == null) {
					continue;
				}
				BlockPos local = at.subtract(hologram.origin());
				if (hologram.isBuilt(local)) {
					continue;
				}
				List<ItemStack> contents = hologram.contentsAt(mc, at);
				return contents == null ? null : new Found(at, state, contents);
			}
			if (mc.level.getBlockState(at).canOcclude()) {
				return null;
			}
		}
		return null;
	}

	/** A container of a hologram and what it holds. */
	public record Found(BlockPos pos, BlockState state, List<ItemStack> contents) {}

	static void render(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		Found found = aimed(mc);
		if (found == null) {
			return;
		}
		Component title = found.contents().isEmpty()
			? Component.translatable("holoprint.hud.contents_empty", found.state().getBlock().getName())
			: Component.translatable("holoprint.hud.contents", found.state().getBlock().getName());
		int columns = Math.min(COLUMNS, Math.max(1, found.contents().size()));
		int rows = (found.contents().size() + COLUMNS - 1) / COLUMNS;
		int width = Math.max(columns * SLOT, mc.font.width(title)) + 8;
		int height = 14 + rows * SLOT + 4;
		int x = graphics.guiWidth() / 2 + 16;
		int y = Math.max(4, Math.min(graphics.guiHeight() - height - 4, graphics.guiHeight() / 2 - height / 2));
		if (x + width > graphics.guiWidth() - 4) {
			x = graphics.guiWidth() - width - 4;
		}
		graphics.fill(x, y, x + width, y + height, 0xB0000000);
		graphics.text(mc.font, title, x + 4, y + 4, 0xFFFFD166, true);
		for (int i = 0; i < found.contents().size(); i++) {
			ItemStack stack = found.contents().get(i);
			int sx = x + 4 + (i % COLUMNS) * SLOT;
			int sy = y + 14 + (i / COLUMNS) * SLOT;
			graphics.item(stack, sx, sy);
			graphics.itemDecorations(mc.font, stack, sx, sy);
		}
	}
}
