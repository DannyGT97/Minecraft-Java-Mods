package io.github.dannygt97.holoprint.schematic;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

/** Where schematic files are read from and saved to. */
public final class SchematicFolder {
	private SchematicFolder() {}

	public static Path path() {
		return FabricLoader.getInstance().getGameDir().resolve("schematics");
	}
}
