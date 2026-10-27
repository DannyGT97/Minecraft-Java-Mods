package io.github.dannygt97.holoprint.placement;

import java.io.IOException;
import java.nio.file.Path;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.hologram.Hologram;
import io.github.dannygt97.holoprint.hologram.HologramRenderer;
import io.github.dannygt97.holoprint.schematic.SchematicFormats;
import io.github.dannygt97.holoprint.schematic.Schematic;
import io.github.dannygt97.holoprint.ui.Notices;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Loads a schematic file as a new placement, a few blocks in front of the camera. */
public final class PlacementLoader {
	private static final double DISTANCE = 10.0;

	private PlacementLoader() {}

	/** True for the file types Holoprint reads today. */
	public static boolean isSupported(Path file) {
		return SchematicFormats.isSupported(file);
	}

	/** Reads the file in the background and adds it as a placement when done. */
	public static void load(Minecraft mc, Path file) {
		if (!isSupported(file)) {
			Notices.error(Component.translatable("holoprint.message.format_unsupported", file.getFileName().toString()));
			return;
		}
		if (HologramRenderer.all().size() >= PlacementLimits.max()) {
			Notices.warning(Component.translatable("holoprint.message.too_many_placements", PlacementLimits.max()));
			return;
		}
		Editor.message(mc, Component.translatable("holoprint.message.loading", file.getFileName().toString()));
		new Thread(() -> {
			try {
				Schematic schematic = SchematicFormats.read(file);
				schematic.source(file);
				String fileName = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
				if (schematic.name() == null || schematic.name().isBlank() || schematic.name().equals("Unnamed")) {
					schematic.name(fileName);
				}
				// Turning the blocks into the hologram is the heavy part for a big schematic, so it is
				// done here, off the game thread, and the game only receives the finished hologram.
				Hologram hologram = new Hologram(schematic, BlockPos.ZERO);
				mc.execute(() -> {
					if (mc.player == null) {
						return;
					}
					// It always starts in front of the camera, a little further for a wide build so that the camera
					// is not inside it, and rests on the ground there when there is ground close below.
					Vec3 eye = Freecam.isActive() ? Freecam.eyePosition() : mc.player.getEyePosition();
					Vec3 look = Freecam.isActive() ? Freecam.lookVector() : mc.player.getLookAngle();
					double distance = DISTANCE + Math.max(hologram.sizeX(), hologram.sizeZ()) / 2.0;
					hologram.placeCentered(Editor.groundNear(mc, BlockPos.containing(eye.add(look.scale(distance)))));
					HologramRenderer.addAndActivate(hologram);
					Editor.message(mc, Component.translatable("holoprint.message.loaded", schematic.totalBlocks(),
						hologram.sizeX(), hologram.sizeY(), hologram.sizeZ()));
					if (!schematic.unknownBlocks().isEmpty()) {
						Editor.message(mc, Component.translatable("holoprint.message.unknown", schematic.unknownCount(), schematic.unknownBlocks().size(), String.join(", ", schematic.unknownBlocks().stream().limit(3).toList())));
					}
					Editor.showNewPlacement(mc);
				});
			} catch (IOException | RuntimeException e) {
				mc.execute(() -> Notices.error(Component.translatable("holoprint.message.load_failed", String.valueOf(e.getMessage()))));
			}
		}, "Holoprint schematic loader").start();
	}
}
