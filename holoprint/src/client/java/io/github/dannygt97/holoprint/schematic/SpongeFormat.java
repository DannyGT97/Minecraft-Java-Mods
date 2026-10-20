package io.github.dannygt97.holoprint.schematic;

import java.io.IOException;
import java.nio.file.Path;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.fixes.References;

/**
 * Reads the Sponge schematic format used by WorldEdit ({@code .schem}), versions 1, 2 and 3: gzip NBT
 * with a palette of block states written as text and one varint per position. Version 3 wraps
 * everything in a {@code Schematic} compound with the blocks in a {@code Blocks} compound.
 */
public final class SpongeFormat {
	/** The data version assumed for version 1 files, which do not say (Minecraft 1.13.2). */
	private static final int ASSUMED_DATA_VERSION = 1631;

	private SpongeFormat() {}

	public static Schematic read(Path file) throws IOException {
		CompoundTag root = SchematicFormats.readNbt(file);
		CompoundTag schem = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;
		int version = schem.getIntOr("Version", 1);
		if (version < 1 || version > 3) {
			throw new IOException("Not a supported .schem file (version " + version + ")");
		}
		int width = schem.getShortOr("Width", (short) 0) & 0xFFFF;
		int height = schem.getShortOr("Height", (short) 0) & 0xFFFF;
		int length = schem.getShortOr("Length", (short) 0) & 0xFFFF;
		if (width == 0 || height == 0 || length == 0) {
			throw new IOException("The .schem file has an empty size");
		}
		int dataVersion = schem.getIntOr("DataVersion", ASSUMED_DATA_VERSION);

		Schematic schematic = new Schematic();
		schematic.dataVersion(dataVersion);
		CompoundTag meta = schem.getCompoundOrEmpty("Metadata");
		schematic.name(meta.getStringOr("Name", ""));
		schematic.author(meta.getStringOr("Author", ""));

		CompoundTag blocks = version >= 3 ? schem.getCompoundOrEmpty("Blocks") : schem;
		CompoundTag paletteTag = blocks.getCompoundOrEmpty("Palette");
		byte[] data = (version >= 3 ? blocks.getByteArray("Data") : blocks.getByteArray("BlockData"))
			.orElseThrow(() -> new IOException("The .schem file has no block data"));

		Region region = new Region("Main", BlockPos.ZERO, width, height, length);
		int largest = -1;
		for (String key : paletteTag.keySet()) {
			largest = Math.max(largest, paletteTag.getIntOr(key, -1));
		}
		PaletteReader palette = new PaletteReader(region, schematic, largest + 1);
		boolean[] defined = new boolean[largest + 1];
		for (String key : paletteTag.keySet()) {
			int number = paletteTag.getIntOr(key, -1);
			if (number >= 0) {
				palette.define(number, LitematicFormat.upgrade(References.BLOCK_STATE, BlockStateTags.parse(key), dataVersion));
				defined[number] = true;
			}
		}

		int[] indices = region.rawIndices();
		int at = 0;
		for (int i = 0; i < indices.length; i++) {
			int value = 0;
			int shift = 0;
			while (true) {
				if (at >= data.length) {
					throw new IOException("The .schem block data ends early");
				}
				int b = data[at++];
				value |= (b & 0x7F) << shift;
				if ((b & 0x80) == 0) {
					break;
				}
				shift += 7;
				if (shift > 28) {
					throw new IOException("The .schem block data is damaged");
				}
			}
			if (value < 0 || value >= defined.length || !defined[value]) {
				throw new IOException("The .schem file uses palette entry " + value + " that its palette does not have");
			}
			indices[i] = palette.map(value);
		}
		palette.finish();

		ListTag blockEntities = (version >= 3 ? blocks : schem).getListOrEmpty("BlockEntities");
		for (int i = 0; i < blockEntities.size(); i++) {
			CompoundTag entry = blockEntities.getCompoundOrEmpty(i);
			int[] pos = entry.getIntArray("Pos").orElse(null);
			if (pos == null || pos.length < 3) {
				continue;
			}
			CompoundTag tag = version >= 3 ? entry.getCompoundOrEmpty("Data").copy() : entry.copy();
			if (version < 3) {
				tag.remove("Pos");
			}
			tag.putString("id", entry.getStringOr("Id", ""));
			tag.remove("Id");
			tag.putInt("x", pos[0]);
			tag.putInt("y", pos[1]);
			tag.putInt("z", pos[2]);
			region.blockEntities().put(new BlockPos(pos[0], pos[1], pos[2]), LitematicFormat.upgrade(References.BLOCK_ENTITY, tag, dataVersion));
		}

		ListTag entities = schem.getListOrEmpty("Entities");
		for (int i = 0; i < entities.size(); i++) {
			CompoundTag entry = entities.getCompoundOrEmpty(i);
			CompoundTag tag = version >= 3 ? entry.getCompoundOrEmpty("Data").copy() : entry.copy();
			tag.putString("id", entry.getStringOr("Id", ""));
			tag.remove("Id");
			Tag pos = entry.get("Pos");
			if (pos != null) {
				tag.put("Pos", pos.copy());
			}
			region.entities().add(LitematicFormat.upgrade(References.ENTITY, tag, dataVersion));
		}
		schematic.regions().add(region);
		return schematic;
	}
}
