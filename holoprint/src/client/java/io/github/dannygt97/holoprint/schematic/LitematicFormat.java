package io.github.dannygt97.holoprint.schematic;

import java.io.IOException;
import java.nio.file.Path;

import com.mojang.datafixers.DSL;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads and writes the .litematic container: gzip-compressed NBT with a Metadata block and a set of
 * named regions, each holding a palette of block states and one palette index per position packed
 * into a long array. The layout is the documented, public one used by the Litematica mod, so files
 * made by either mod open in the other.
 */
public final class LitematicFormat {
	/** Newest file version understood (7 stores block entities as 1.20.5+ data components). */
	public static final int MAX_VERSION = 7;
	private static final int WRITE_VERSION = 7;
	private static final int WRITE_SUBVERSION = 1;

	private LitematicFormat() {}

	public static Schematic read(Path file) throws IOException {
		CompoundTag root = SchematicFormats.readNbt(file);
		int version = root.getIntOr("Version", -1);
		if (version < 1 || version > MAX_VERSION) {
			throw new IOException("Not a supported .litematic file (version " + version + ")");
		}

		Schematic schematic = new Schematic();
		schematic.dataVersion(root.getIntOr("MinecraftDataVersion", 0));
		CompoundTag meta = root.getCompoundOrEmpty("Metadata");
		schematic.name(meta.getStringOr("Name", ""));
		schematic.author(meta.getStringOr("Author", ""));
		schematic.description(meta.getStringOr("Description", ""));
		schematic.timeCreated(meta.getLongOr("TimeCreated", 0L));
		schematic.timeModified(meta.getLongOr("TimeModified", 0L));

		CompoundTag regions = root.getCompoundOrEmpty("Regions");
		for (String regionName : regions.keySet()) {
			CompoundTag tag = regions.getCompoundOrEmpty(regionName);
			if (tag.contains("BlockStates")) {
				schematic.regions().add(readRegion(regionName, tag, schematic));
			}
		}
		return schematic;
	}

	/** What the file says about itself, read without building any blocks. */
	public record Info(String name, String author, String description, long timeCreated, long timeModified, int dataVersion,
		int totalBlocks, int totalVolume, int regionCount, int sizeX, int sizeY, int sizeZ, int version) {}

	/** Reads only the descriptive data of a .litematic file, for the file list. */
	public static Info readInfo(Path file) throws IOException {
		CompoundTag root = SchematicFormats.readNbt(file);
		int version = root.getIntOr("Version", -1);
		if (version < 1 || version > MAX_VERSION) {
			throw new IOException("Not a supported .litematic file (version " + version + ")");
		}
		CompoundTag meta = root.getCompoundOrEmpty("Metadata");
		CompoundTag size = meta.getCompoundOrEmpty("EnclosingSize");
		return new Info(meta.getStringOr("Name", ""), meta.getStringOr("Author", ""), meta.getStringOr("Description", ""),
			meta.getLongOr("TimeCreated", 0L), meta.getLongOr("TimeModified", 0L), root.getIntOr("MinecraftDataVersion", 0),
			meta.getIntOr("TotalBlocks", 0), meta.getIntOr("TotalVolume", 0), meta.getIntOr("RegionCount", root.getCompoundOrEmpty("Regions").size()),
			size.getIntOr("x", 0), size.getIntOr("y", 0), size.getIntOr("z", 0), version);
	}

	private static Region readRegion(String name, CompoundTag tag, Schematic schematic) throws IOException {
		BlockPos position = readPos(tag.getCompoundOrEmpty("Position"));
		BlockPos size = readPos(tag.getCompoundOrEmpty("Size"));
		if (size.getX() == 0 || size.getY() == 0 || size.getZ() == 0) {
			throw new IOException("Region " + name + " has an empty size");
		}
		// A negative size means the box extends backwards from its stored position.
		BlockPos origin = new BlockPos(
			size.getX() >= 0 ? position.getX() : position.getX() + size.getX() + 1,
			size.getY() >= 0 ? position.getY() : position.getY() + size.getY() + 1,
			size.getZ() >= 0 ? position.getZ() : position.getZ() + size.getZ() + 1
		);
		Region region = new Region(name, origin, Math.abs(size.getX()), Math.abs(size.getY()), Math.abs(size.getZ()));
		region.entityBase(position);

		ListTag paletteTag = tag.getListOrEmpty("BlockStatePalette");
		PaletteReader palette = new PaletteReader(region, schematic, paletteTag.size());
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.define(i, upgrade(References.BLOCK_STATE, paletteTag.getCompoundOrEmpty(i), schematic.dataVersion()));
		}

		long[] packed = tag.getLongArray("BlockStates").orElseThrow(() -> new IOException("Region " + name + " has no block data"));
		BitArray bits = new BitArray(BitArray.widthFor(Math.max(1, paletteTag.size())), region.volume(), packed);
		int[] indices = region.rawIndices();
		for (int i = 0; i < indices.length; i++) {
			indices[i] = palette.map(bits.get(i));
		}
		palette.finish();

		ListTag blockEntities = tag.getListOrEmpty("TileEntities");
		for (int i = 0; i < blockEntities.size(); i++) {
			CompoundTag entry = blockEntities.getCompoundOrEmpty(i);
			BlockPos where = new BlockPos(entry.getIntOr("x", 0), entry.getIntOr("y", 0), entry.getIntOr("z", 0));
			region.blockEntities().put(where, upgrade(References.BLOCK_ENTITY, entry, schematic.dataVersion()));
		}
		ListTag entities = tag.getListOrEmpty("Entities");
		for (int i = 0; i < entities.size(); i++) {
			region.entities().add(upgrade(References.ENTITY, entities.getCompoundOrEmpty(i), schematic.dataVersion()));
		}
		return region;
	}

	/**
	 * Brings data saved by an older game version up to this one with the game's own data fixer, so
	 * renamed blocks and changed block entity or entity formats still load. When the fixer cannot
	 * handle an entry it is used as it is.
	 */
	static CompoundTag upgrade(DSL.TypeReference type, CompoundTag tag, int fromVersion) {
		int toVersion = SharedConstants.getCurrentVersion().dataVersion().version();
		if (fromVersion <= 0 || fromVersion >= toVersion) {
			return tag.copy();
		}
		try {
			Tag fixed = DataFixers.getDataFixer().update(type, new Dynamic<>(NbtOps.INSTANCE, tag.copy()), fromVersion, toVersion).getValue();
			return fixed instanceof CompoundTag compound ? compound : tag.copy();
		} catch (RuntimeException e) {
			return tag.copy();
		}
	}

	public static void write(Schematic schematic, Path file) throws IOException {
		CompoundTag root = new CompoundTag();
		root.putInt("MinecraftDataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
		root.putInt("Version", WRITE_VERSION);
		root.putInt("SubVersion", WRITE_SUBVERSION);

		CompoundTag meta = new CompoundTag();
		meta.putString("Name", schematic.name());
		meta.putString("Author", schematic.author());
		meta.putString("Description", schematic.description());
		meta.putInt("RegionCount", schematic.regions().size());
		meta.putInt("TotalVolume", (int) schematic.totalVolume());
		meta.putInt("TotalBlocks", (int) schematic.totalBlocks());
		long now = System.currentTimeMillis();
		meta.putLong("TimeCreated", schematic.timeCreated() > 0 ? schematic.timeCreated() : now);
		meta.putLong("TimeModified", now);
		meta.put("EnclosingSize", writePos(schematic.enclosingSize()));
		root.put("Metadata", meta);

		CompoundTag regions = new CompoundTag();
		for (Region region : schematic.regions()) {
			regions.put(region.name(), writeRegion(region));
		}
		root.put("Regions", regions);
		NbtIo.writeCompressed(root, file);
	}

	private static CompoundTag writeRegion(Region region) {
		CompoundTag tag = new CompoundTag();
		tag.put("Position", writePos(region.origin()));
		tag.put("Size", writePos(new BlockPos(region.sizeX(), region.sizeY(), region.sizeZ())));

		ListTag palette = new ListTag();
		for (BlockState state : region.palette()) {
			palette.add(BlockStateTags.write(state));
		}
		tag.put("BlockStatePalette", palette);

		BitArray bits = new BitArray(BitArray.widthFor(region.palette().size()), region.volume());
		int[] indices = region.rawIndices();
		for (int i = 0; i < indices.length; i++) {
			bits.set(i, indices[i]);
		}
		tag.putLongArray("BlockStates", bits.data());

		ListTag blockEntities = new ListTag();
		region.blockEntities().forEach((pos, data) -> {
			CompoundTag copy = data.copy();
			copy.putInt("x", pos.getX());
			copy.putInt("y", pos.getY());
			copy.putInt("z", pos.getZ());
			blockEntities.add(copy);
		});
		tag.put("TileEntities", blockEntities);

		ListTag entities = new ListTag();
		for (CompoundTag entity : region.entities()) {
			CompoundTag copy = entity.copy();
			// The file stores positions relative to the region's Position, which this writer sets to the minimum corner.
			BlockPos shift = region.entityBase().subtract(region.origin());
			ListTag pos = copy.getListOrEmpty("Pos");
			if (pos.size() >= 3 && !shift.equals(BlockPos.ZERO)) {
				ListTag moved = new ListTag();
				moved.add(net.minecraft.nbt.DoubleTag.valueOf(pos.getDoubleOr(0, 0.0) + shift.getX()));
				moved.add(net.minecraft.nbt.DoubleTag.valueOf(pos.getDoubleOr(1, 0.0) + shift.getY()));
				moved.add(net.minecraft.nbt.DoubleTag.valueOf(pos.getDoubleOr(2, 0.0) + shift.getZ()));
				copy.put("Pos", moved);
			}
			entities.add(copy);
		}
		tag.put("Entities", entities);
		tag.put("PendingBlockTicks", new ListTag());
		tag.put("PendingFluidTicks", new ListTag());
		return tag;
	}

	private static BlockPos readPos(CompoundTag tag) {
		return new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
	}

	private static Tag writePos(BlockPos pos) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("x", pos.getX());
		tag.putInt("y", pos.getY());
		tag.putInt("z", pos.getZ());
		return tag;
	}
}
