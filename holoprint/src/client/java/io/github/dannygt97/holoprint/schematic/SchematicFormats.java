package io.github.dannygt97.holoprint.schematic;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.InflaterInputStream;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Picks the reader for a schematic file from its extension: {@code .litematic}, {@code .schem} or {@code .schematic}. */
public final class SchematicFormats {
	private SchematicFormats() {}

	private static String extension(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot);
	}

	/** True for the file types Holoprint reads. */
	public static boolean isSupported(Path file) {
		return switch (extension(file)) {
			case ".litematic", ".schem", ".schematic" -> true;
			default -> false;
		};
	}

	/**
	 * Reads the NBT of a schematic file whatever its packing: gzip (what the game and every tool write),
	 * zlib, or plain uncompressed NBT (some tools save it that way).
	 */
	public static CompoundTag readNbt(Path file) throws IOException {
		int first, second;
		try (InputStream in = Files.newInputStream(file)) {
			first = in.read();
			second = in.read();
		}
		if (first == 0x1F && second == 0x8B) {
			return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		}
		if (first == 0x78 && (second == 0x01 || second == 0x5E || second == 0x9C || second == 0xDA)) {
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(Files.newInputStream(file))))) {
				return NbtIo.read(in, NbtAccounter.unlimitedHeap());
			}
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
			return NbtIo.read(in, NbtAccounter.unlimitedHeap());
		}
	}

	public static Schematic read(Path file) throws IOException {
		return switch (extension(file)) {
			case ".litematic" -> LitematicFormat.read(file);
			case ".schem" -> SpongeFormat.read(file);
			case ".schematic" -> LegacyFormat.read(file);
			default -> throw new IOException("Unsupported file type: " + file.getFileName());
		};
	}

	/** What a file says about itself, for the file list; the formats without a header are read whole. */
	public static LitematicFormat.Info readInfo(Path file) throws IOException {
		if (extension(file).equals(".litematic")) {
			return LitematicFormat.readInfo(file);
		}
		Schematic schematic = read(file);
		BlockPos size = schematic.enclosingSize();
		long modified = Files.getLastModifiedTime(file).toMillis();
		return new LitematicFormat.Info(schematic.name(), schematic.author(), schematic.description(), modified, modified, schematic.dataVersion(),
			(int) Math.min(Integer.MAX_VALUE, schematic.totalBlocks()), (int) Math.min(Integer.MAX_VALUE, schematic.totalVolume()),
			schematic.regions().size(), size.getX(), size.getY(), size.getZ(), 0);
	}
}
