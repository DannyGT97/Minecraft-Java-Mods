package io.github.dannygt97.holoprint.schematic;

/**
 * Fixed-width unsigned values packed back to back into longs, the way .litematic stores the block
 * of every position: entry i takes bits [i * width, (i + 1) * width), and an entry may straddle two
 * longs.
 */
public final class BitArray {
	private final long[] data;
	private final int width;
	private final long mask;
	private final long size;

	public BitArray(int width, long size) {
		this(width, size, new long[(int) ((size * width + 63) / 64)]);
	}

	public BitArray(int width, long size, long[] data) {
		if (width < 1 || width > 32) {
			throw new IllegalArgumentException("Bit width out of range: " + width);
		}
		long needed = (size * width + 63) / 64;
		if (data.length < needed) {
			throw new IllegalArgumentException("Block data has " + data.length + " longs, " + needed + " are needed");
		}
		this.width = width;
		this.size = size;
		this.mask = (1L << width) - 1L;
		this.data = data;
	}

	public int get(long index) {
		long start = index * width;
		int first = (int) (start >> 6);
		int last = (int) (((index + 1) * width - 1) >> 6);
		int offset = (int) (start & 63);
		if (first == last) {
			return (int) (data[first] >>> offset & mask);
		}
		return (int) ((data[first] >>> offset | data[last] << (64 - offset)) & mask);
	}

	public void set(long index, int value) {
		long start = index * width;
		int first = (int) (start >> 6);
		int last = (int) (((index + 1) * width - 1) >> 6);
		int offset = (int) (start & 63);
		long v = value & mask;
		data[first] = data[first] & ~(mask << offset) | v << offset;
		if (first != last) {
			int shift = 64 - offset;
			int remaining = width - shift;
			data[last] = data[last] >>> remaining << remaining | v >> shift;
		}
	}

	public long[] data() {
		return data;
	}

	public long size() {
		return size;
	}

	/** Bits needed per entry for a palette of {@code paletteSize} states (never fewer than 2). */
	public static int widthFor(int paletteSize) {
		return Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
	}
}
