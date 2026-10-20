package io.github.dannygt97.holoprint.schematic;

import java.io.IOException;
import java.nio.file.Path;

import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.fixes.BlockEntityIdFix;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.References;

/**
 * Reads the old MCEdit and Schematica format ({@code .schematic}): blocks as numeric ids with a data
 * value, from before Minecraft 1.13. The game's own flattening table turns every id and data value
 * into a block state, and the data fixer takes it from there.
 */
public final class LegacyFormat {
	/** Minecraft 1.12.2, the newest game these files come from; entities and block entities are fixed from here. */
	private static final int LEGACY_DATA_VERSION = 1343;
	/** The version the flattening table speaks (the first 1.13 data version). */
	private static final int FLATTENED_DATA_VERSION = 1451;

	private LegacyFormat() {}

	public static Schematic read(Path file) throws IOException {
		CompoundTag root = SchematicFormats.readNbt(file);
		CompoundTag tag = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;
		int width = tag.getShortOr("Width", (short) 0) & 0xFFFF;
		int height = tag.getShortOr("Height", (short) 0) & 0xFFFF;
		int length = tag.getShortOr("Length", (short) 0) & 0xFFFF;
		if (width == 0 || height == 0 || length == 0) {
			throw new IOException("The .schematic file has an empty size");
		}
		byte[] ids = tag.getByteArray("Blocks").orElseThrow(() -> new IOException("The .schematic file has no block data"));
		byte[] data = tag.getByteArray("Data").orElseThrow(() -> new IOException("The .schematic file has no block data"));
		byte[] add = tag.getByteArray("AddBlocks").orElse(null);

		Schematic schematic = new Schematic();
		schematic.dataVersion(LEGACY_DATA_VERSION);
		Region region = new Region("Main", BlockPos.ZERO, width, height, length);
		long volume = region.volume();
		if (ids.length < volume || data.length < volume) {
			throw new IOException("The .schematic block data is shorter than its size");
		}

		// Each (id, data) pair becomes one palette entry the first time it is seen.
		java.util.Map<Integer, Integer> entryOf = new java.util.HashMap<>();
		java.util.List<Integer> pairs = new java.util.ArrayList<>();
		int[] indices = region.rawIndices();
		int[] pairAt = new int[indices.length];
		for (int i = 0; i < indices.length; i++) {
			int id = ids[i] & 0xFF;
			if (add != null && (i >> 1) < add.length) {
				id |= ((i & 1) == 0 ? add[i >> 1] & 0x0F : (add[i >> 1] >> 4) & 0x0F) << 8;
			}
			int pair = (id << 4) | (data[i] & 0x0F);
			Integer number = entryOf.get(pair);
			if (number == null) {
				number = pairs.size();
				entryOf.put(pair, number);
				pairs.add(pair);
			}
			pairAt[i] = number;
		}
		PaletteReader palette = new PaletteReader(region, schematic, pairs.size());
		for (int n = 0; n < pairs.size(); n++) {
			palette.define(n, flatten(pairs.get(n)));
		}
		for (int i = 0; i < indices.length; i++) {
			indices[i] = palette.map(pairAt[i]);
		}
		palette.finish();

		ListTag blockEntities = tag.getListOrEmpty("TileEntities");
		for (int i = 0; i < blockEntities.size(); i++) {
			CompoundTag entry = blockEntities.getCompoundOrEmpty(i);
			BlockPos where = new BlockPos(entry.getIntOr("x", 0), entry.getIntOr("y", 0), entry.getIntOr("z", 0));
			CompoundTag fixed = LitematicFormat.upgrade(References.BLOCK_ENTITY, entry, LEGACY_DATA_VERSION);
			// The game's fixer renames block entity ids only inside whole chunks, so the old names are mapped here.
			String renamed = BlockEntityIdFix.ID_MAP.get(fixed.getStringOr("id", ""));
			if (renamed != null) {
				fixed.putString("id", renamed);
			}
			region.blockEntities().put(where, fixed);
		}
		ListTag entities = tag.getListOrEmpty("Entities");
		for (int i = 0; i < entities.size(); i++) {
			CompoundTag entity = LitematicFormat.upgrade(References.ENTITY, entities.getCompoundOrEmpty(i), LEGACY_DATA_VERSION);
			String entityId = legacyEntityIds().get(entity.getStringOr("id", ""));
			if (entityId != null) {
				entity.putString("id", entityId);
			}
			region.entities().add(entity);
		}
		schematic.regions().add(region);
		return schematic;
	}

	/** The old entity names (Villager, Zombie...) and the ids that replaced them; the game keeps the table private. */
	@SuppressWarnings("unchecked")
	private static java.util.Map<String, String> legacyEntityIds() {
		try {
			java.lang.reflect.Field field = net.minecraft.util.datafix.fixes.EntityIdFix.class.getDeclaredField("ID_MAP");
			field.setAccessible(true);
			return (java.util.Map<String, String>) field.get(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return java.util.Map.of();
		}
	}

	/** The current block state for a legacy id and data value, as a palette entry. */
	static CompoundTag flatten(int idAndData) {
		Dynamic<?> flat = BlockStateData.getTag(idAndData);
		Tag value = flat.convert(NbtOps.INSTANCE).getValue();
		CompoundTag state = value instanceof CompoundTag compound ? compound : new CompoundTag();
		return LitematicFormat.upgrade(References.BLOCK_STATE, state, FLATTENED_DATA_VERSION);
	}
}
