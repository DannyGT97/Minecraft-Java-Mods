package com.dstsoftware.treechopper.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Items;

// Chopping a log while sneaking with an axe fells the connected logs above the cut and their attached leaf canopy.
public class FellingHandler {
	// Generous but bounded, so an enormous mega-tree (or a forest of touching logs) can't become
	// a lag/performance exploit from one right-click.
	private static final int MAX_LOGS = 512;
	private static final int MAX_LEAVES = 1024;
	private static final int MAX_NETHER_CANOPY = 1024;
	private static final float[] NETHER_FUNGUS_DROP_CHANCES = {0.05F, 0.0625F, 0.083333336F, 0.1F};
	private static final RandomSource RANDOM = RandomSource.create();

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register(FellingHandler::onBlockBroken);
	}

	private static void onBlockBroken(
		final Level level, final Player player, final BlockPos pos, final BlockState state, final BlockEntity blockEntity
	) {
		if (!(level instanceof ServerLevel serverLevel) || !state.is(BlockTags.LOGS) || !player.isShiftKeyDown() || !player.getMainHandItem().is(ItemTags.AXES)) {
			return;
		}

		ItemStack held = player.getMainHandItem();

		// A thick multi-log trunk (dark oak/pale oak's 2x2 base, a mega spruce/jungle trunk grown
		// from 4 saplings) still has another log of the same type standing right beside this one
		// at the exact same height while any of its other base logs remain - don't fell yet in
		// that case, so a thick trunk is only felled once that layer is cleared. Only the log that
		// finally clears that whole layer (no matching neighbor left at this height) triggers the
		// fell. A normal single-wide trunk never has a same-height neighbor at all, so this never
		// holds back felling for the common case.
		Block logBlock = state.getBlock();
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockState neighbor = serverLevel.getBlockState(pos.relative(dir));
			if (neighbor.is(BlockTags.LOGS) && sameWoodFamily(logBlock, neighbor.getBlock())) {
				return;
			}
		}

		// This is the AFTER event - `pos` is already air by the time we get here, so it can't be
		// used as a BFS seed itself (it would just fail the "is this block a log" match). Seed
		// with its neighbors instead; only the rest of the connected tree is ours to break here.
		// Diagonal adjacency is required here too, not just for leaves - acacia's own trunk
		// placer angles the branch diagonally, so consecutive trunk logs are frequently only
		// edge/corner-adjacent rather than sharing a full face; a face-only (6-directional) BFS
		// stops early partway up a diagonal branch and leaves the rest of that tree standing.
		// The `p.getY() >= pos.getY()` guard is what makes this a directional felling instead of
		// destroying the whole tree regardless of cut height: chopping partway up a thick
		// multi-log trunk (dark oak/pale oak's 2x2 base) should only take down what's above the
		// cut, leaving the stump below standing, exactly like chopping a real tree.
		Set<BlockPos> logs = new HashSet<>();
		floodFill(
			serverLevel, logs, neighborsOf(pos, true), p -> p.getY() >= pos.getY() && serverLevel.getBlockState(p).is(BlockTags.LOGS) && sameWoodFamily(logBlock, serverLevel.getBlockState(p).getBlock()), true,
			MAX_LOGS
		);

		Set<BlockPos> leaves = isNetherStem(logBlock) ? Set.of() : collectLeaves(serverLevel, logs, pos);
		if (isNetherStem(logBlock)) {
			fellNetherCanopy(serverLevel, held, pos, logs);
		}
		for (BlockPos logPos : logs) {
			serverLevel.destroyBlock(logPos, true, player, 512);
			held.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		}
		for (BlockPos leafPos : leaves) {
			serverLevel.destroyBlock(leafPos, true, player, 512);
		}
	}

	// Breadth-first flood fill outward from `seeds`, adding every reachable position matching
	// `matches` into `found`. `includeDiagonals` scans all 26 neighbors instead of just the 6
	// face-adjacent ones - needed here since acacia's trunk placer angles its branch diagonally,
	// so consecutive trunk logs are frequently only edge/corner-adjacent rather than sharing a
	// full face.
	private static void floodFill(
		final ServerLevel level, final Set<BlockPos> found, final Iterable<BlockPos> seeds, final Predicate<BlockPos> matches,
		final boolean includeDiagonals, final int cap
	) {
		Deque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos seed : seeds) {
			if (found.size() < cap && !found.contains(seed) && matches.test(seed)) {
				found.add(seed);
				queue.add(seed);
			}
		}

		while (!queue.isEmpty() && found.size() < cap) {
			BlockPos current = queue.poll();
			for (BlockPos neighbor : neighborsOf(current, includeDiagonals)) {
				if (found.size() >= cap || found.contains(neighbor) || !matches.test(neighbor)) {
					continue;
				}
				found.add(neighbor);
				queue.add(neighbor);
			}
		}
	}

	private static List<BlockPos> neighborsOf(final BlockPos pos, final boolean includeDiagonals) {
		if (!includeDiagonals) {
			List<BlockPos> result = new ArrayList<>(6);
			for (Direction dir : Direction.values()) {
				result.add(pos.relative(dir));
			}
			return result;
		}

		List<BlockPos> result = new ArrayList<>(26);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					result.add(pos.offset(dx, dy, dz));
				}
			}
		}
		return result;
	}

	// Follows vanilla's own leaf-support chain instead of scanning a fixed radius: every leaves
	// block already carries a `distance` property (1-7) that vanilla itself maintains as "how far
	// is this leaf, through a chain of touching leaves, from the nearest log" - the same value it
	// uses to decide whether an unsupported leaf decays. Seeding from leaves directly touching a
	// felled log (distance == 1, unambiguous - touching one of our own logs always means exactly
	// that) and only continuing to a neighbor whose distance is exactly one more than the current
	// leaf's catches the whole real canopy (including acacia's sparse, barely-touching clusters)
	// without spilling into a separate, closely-planted tree's leaves - those sit on their own
	// shorter chain back to their own logs, so their distance values never line up with a
	// continuation of this tree's chain. A fixed-radius scan can't tell the two apart; this can,
	// because it's the same topology vanilla itself already computed.
	private static Set<BlockPos> collectLeaves(
		final ServerLevel level, final Set<BlockPos> logs, final BlockPos originPos
	) {
		Set<BlockPos> leaves = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();

		List<BlockPos> seeds = new ArrayList<>();
		for (BlockPos logPos : logs) {
			seeds.addAll(neighborsOf(logPos, true));
		}
		seeds.addAll(neighborsOf(originPos, true));

		for (BlockPos seed : seeds) {
			if (leaves.size() >= MAX_LEAVES || leaves.contains(seed) || seed.getY() < originPos.getY()) {
				continue;
			}
			BlockState state = level.getBlockState(seed);
			if (state.is(BlockTags.LEAVES) && LeavesBlock.getOptionalDistanceAt(state).orElse(-1) == 1) {
				leaves.add(seed);
				queue.add(seed);
			}
		}

		while (!queue.isEmpty() && leaves.size() < MAX_LEAVES) {
			BlockPos current = queue.poll();
			int currentDistance = LeavesBlock.getOptionalDistanceAt(level.getBlockState(current)).orElse(LeavesBlock.DECAY_DISTANCE);
			for (BlockPos neighbor : neighborsOf(current, true)) {
				if (leaves.size() >= MAX_LEAVES || leaves.contains(neighbor) || neighbor.getY() < originPos.getY()) {
					continue;
				}
				BlockState state = level.getBlockState(neighbor);
				if (!state.is(BlockTags.LEAVES)) {
					continue;
				}
				OptionalInt neighborDistance = LeavesBlock.getOptionalDistanceAt(state);
				if (neighborDistance.isPresent() && neighborDistance.getAsInt() == currentDistance + 1) {
					leaves.add(neighbor);
					queue.add(neighbor);
				}
			}
		}
		return leaves;
	}

	private static boolean sameWoodFamily(final Block first, final Block second) {
		Identifier firstId = BuiltInRegistries.BLOCK.getKey(first);
		Identifier secondId = BuiltInRegistries.BLOCK.getKey(second);
		return firstId.getNamespace().equals(secondId.getNamespace()) && woodFamily(firstId.getPath()).equals(woodFamily(secondId.getPath()));
	}

	private static String woodFamily(String path) {
		if (path.startsWith("stripped_")) path = path.substring("stripped_".length());
		for (String suffix : new String[] {"_log", "_wood", "_stem", "_hyphae"}) {
			if (path.endsWith(suffix)) return path.substring(0, path.length() - suffix.length());
		}
		return path;
	}

	private static boolean isNetherStem(Block block) {
		return block == Blocks.CRIMSON_STEM || block == Blocks.WARPED_STEM;
	}

	private static void fellNetherCanopy(ServerLevel level, ItemStack axe, BlockPos origin, Set<BlockPos> logs) {
		Set<BlockPos> canopy = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		List<BlockPos> seeds = new ArrayList<>(neighborsOf(origin, true));
		for (BlockPos log : logs) seeds.addAll(neighborsOf(log, true));
		for (BlockPos seed : seeds) {
			if (canopy.size() < MAX_NETHER_CANOPY && isNetherCanopy(level.getBlockState(seed)) && canopy.add(seed)) queue.add(seed);
		}
		while (!queue.isEmpty() && canopy.size() < MAX_NETHER_CANOPY) {
			for (BlockPos next : neighborsOf(queue.remove(), true)) {
				if (canopy.size() >= MAX_NETHER_CANOPY) break;
				if (isNetherCanopy(level.getBlockState(next)) && canopy.add(next)) queue.add(next);
			}
		}
		int fortuneLevel = fortuneLevel(level, axe);
		float fungusDropChance = NETHER_FUNGUS_DROP_CHANCES[Math.min(fortuneLevel, NETHER_FUNGUS_DROP_CHANCES.length - 1)];
		for (BlockPos canopyPos : canopy) {
			Block block = level.getBlockState(canopyPos).getBlock();
			level.removeBlock(canopyPos, false);
			if (block == Blocks.SHROOMLIGHT) {
				Block.popResource(level, canopyPos, new ItemStack(Items.SHROOMLIGHT));
			} else if (RANDOM.nextFloat() < fungusDropChance) {
				ItemStack fungus = new ItemStack(block == Blocks.NETHER_WART_BLOCK ? Items.CRIMSON_FUNGUS : Items.WARPED_FUNGUS);
				Block.popResource(level, canopyPos, fungus);
			}
		}
	}

	private static boolean isNetherCanopy(BlockState state) {
		return state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.WARPED_WART_BLOCK) || state.is(Blocks.SHROOMLIGHT);
	}

	private static int fortuneLevel(ServerLevel level, ItemStack stack) {
		Holder<Enchantment> fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
		return EnchantmentHelper.getItemEnchantmentLevel(fortune, stack);
	}
}



