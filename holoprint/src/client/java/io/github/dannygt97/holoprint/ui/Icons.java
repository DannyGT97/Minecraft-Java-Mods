package io.github.dannygt97.holoprint.ui;

import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small pixel-art pictures for the toolbar, drawn with plain filled squares so that they need no
 * image files and scale with the interface. A '#' is a filled pixel.
 */
public final class Icons {
	private static final Map<String, String[]> ICONS = Map.ofEntries(
		Map.entry("M", new String[] {
			"....#....",
			"...###...",
			"....#....",
			".#..#..#.",
			"#########",
			".#..#..#.",
			"....#....",
			"...###...",
			"....#...."}),
		Map.entry("S", new String[] {
			"#.#.#.#.#",
			".........",
			"#.......#",
			".........",
			"#.......#",
			".........",
			"#.......#",
			".........",
			"#.#.#.#.#"}),
		Map.entry("C", new String[] {
			"...###...",
			"...#.#...",
			"...###...",
			"....#....",
			".#######.",
			".#.....#.",
			".#######."}),
		Map.entry("D", new String[] {
			"#.......#",
			".#.....#.",
			"..#...#..",
			"...#.#...",
			"....#....",
			"...#.#...",
			"..#...#..",
			".#.....#.",
			"#.......#"}),
		Map.entry("Ma", new String[] {
			"#.#######",
			".........",
			"#.#####..",
			".........",
			"#.#######",
			".........",
			"#.####..."}),
		Map.entry("Cp", new String[] {
			"#####....",
			"#...#....",
			"#...#####",
			"#...#...#",
			"#####...#",
			"....#...#",
			"....#####"}),
		Map.entry("Ct", new String[] {
			"##...##",
			"##...##",
			".#...#.",
			"..#.#..",
			"...#...",
			"..#.#..",
			".#...#.",
			"##...##",
			"##...##"}),
		Map.entry("Pe", new String[] {
			"..###..",
			"#.###.#",
			"#.....#",
			"#.###.#",
			"#.....#",
			"#.###.#",
			"#.....#",
			"#.....#",
			"#######"}),
		Map.entry("Bo", new String[] {
			"..###..",
			"#######",
			".#.#.#.",
			".#.#.#.",
			".#.#.#.",
			".#.#.#.",
			".#####."}),
		Map.entry("Gu", new String[] {
			"########",
			"#.####.#",
			"#.####.#",
			"#......#",
			"#.####.#",
			"#.#..#.#",
			"#.####.#",
			"########"}),
		Map.entry("Ld", new String[] {
			"####.....",
			"#...####.",
			"#.......#",
			"#.......#",
			"#.......#",
			"#.......#",
			"#########"}),
		Map.entry("Pl", new String[] {
			"##.######",
			".........",
			"##.######",
			".........",
			"##.######"}),
		Map.entry("Ar", new String[] {
			"##.....##",
			"#.......#",
			".........",
			".........",
			".........",
			".........",
			".........",
			"#.......#",
			"##.....##"}),
		Map.entry("Cf", new String[] {
			"...###...",
			".#.###.#.",
			"..#####..",
			"###...###",
			"###...###",
			"###...###",
			"..#####..",
			".#.###.#.",
			"...###..."}),
		Map.entry("Bu", new String[] {
			".#######.",
			"#########",
			"#########",
			".#######.",
			"...###...",
			"...###...",
			"...###...",
			"...###...",
			"...###..."}),
		Map.entry("Ly", new String[] {
			"...###...",
			".#######.",
			"...###...",
			".........",
			"#########",
			".........",
			"...###...",
			".#######.",
			"...###..."}),
		Map.entry("Lup", new String[] {
			"....#....",
			"...###...",
			"..#####..",
			".#######.",
			"....#....",
			"....#....",
			"....#...."}),
		Map.entry("Ldn", new String[] {
			"....#....",
			"....#....",
			"....#....",
			".#######.",
			"..#####..",
			"...###...",
			"....#...."}),
		Map.entry("Mn", new String[] {
			"##.##.##",
			"##.##.##"}),
		Map.entry("Eye", new String[] {
			"...#####...",
			".##.....##.",
			"#...###...#",
			"#..#####..#",
			"#...###...#",
			".##.....##.",
			"...#####..."}),
		Map.entry("Lock", new String[] {
			"..###..",
			".#...#.",
			".#...#.",
			"#######",
			"#######",
			"###.###",
			"###.###",
			"#######"}),
		Map.entry("Unlock", new String[] {
			"..###..",
			".#...#.",
			".#.....",
			"#######",
			"#######",
			"###.###",
			"###.###",
			"#######"}));

	private Icons() {}

	public static boolean has(String glyph) {
		return ICONS.containsKey(glyph);
	}

	/** Draws the icon centred in a square of the given size, two screen pixels per icon pixel. */
	public static void draw(GuiGraphicsExtractor graphics, String glyph, int x, int y, int size, int color) {
		String[] rows = ICONS.get(glyph);
		if (rows == null) {
			return;
		}
		int unit = size >= Math.max(rows.length, rows[0].length()) * 2 + 2 ? 2 : 1;
		int width = rows[0].length() * unit;
		int height = rows.length * unit;
		int left = x + (size - width) / 2;
		int top = y + (size - height) / 2;
		for (int row = 0; row < rows.length; row++) {
			for (int col = 0; col < rows[row].length(); col++) {
				if (rows[row].charAt(col) == '#') {
					graphics.fill(left + col * unit, top + row * unit, left + (col + 1) * unit, top + (row + 1) * unit, color);
				}
			}
		}
	}
}
