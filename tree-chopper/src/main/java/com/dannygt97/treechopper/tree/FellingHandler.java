package com.dannygt97.treechopper.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

// Chopping a log while sneaking with an axe fells the connected logs above the cut and their attached leaf canopy.
public class FellingHandler {
	// Generous but bounded, so an enormous mega-tree (or a forest of touching logs) can't become
	// a lag/performance exploit from one right-click.
	private static final int MAX_LOGS = 512;
	private static final int MAX_LEAVES = 1024;
	private static final int MAX_TREE_DECORATIONS = 1024;
	private static final int MAX_NETHER_CANOPY = 1024;
	private static final int MAX_ORPHAN_GROUP = 200;
	private static final float[] NETHER_FUNGUS_DROP_CHANCES = {0.05F, 0.0625F, 0.083333336F, 0.1F};
	private static final RandomSource RANDOM = RandomSource.create();
	// Saplings wait this long (half a second) before being planted so the swing that just felled the
	// tree can't hit them.
	private static final int SAPLING_DELAY_TICKS = 10;
	// Every block broken the vanilla way plays its own break sound and particles, and a whole tree
	// breaking at once stacks hundreds of them into one deafening burst. Only a handful of blocks,
	// spread evenly over the tree, play them; the rest are removed silently.
	private static final int MAX_LOG_EFFECTS = 8;
	private static final int MAX_LEAF_EFFECTS = 4;
	private static final List<PendingSapling> PENDING_SAPLINGS = new ArrayList<>();
	private static long tickCounter;

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register(FellingHandler::onBlockBroken);
		ServerTickEvents.END_SERVER_TICK.register(server -> tickSaplings());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_SAPLINGS.clear());
	}

	private static void onBlockBroken(
		final Level level, final Player player, final BlockPos pos, final BlockState state, final BlockEntity blockEntity
	) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		// Housekeeping that must run whatever the settings are: a block that is gone is no longer
		// "placed by a player", so a tree that later grows on the same spot is natural again. A log
		// the player placed is never the start of a felling - it is part of a build, not a tree.
		boolean playerPlaced = PlayerPlacedBlocks.shouldTrack(state) && PlayerPlacedBlocks.isPlaced(serverLevel, pos);
		if (playerPlaced) {
			PlayerPlacedBlocks.unmark(serverLevel, pos);
		}

		ItemStack held = player.getMainHandItem();
		if (playerPlaced || !TreeChopperConfig.isEnabled() || !state.is(BlockTags.LOGS)
			|| (!TreeChopperConfig.isAllowWithoutSneaking() && !player.isShiftKeyDown())
			|| !held.is(ItemTags.AXES)) {
			return;
		}

		Block logBlock = state.getBlock();
		boolean nether = isNetherWood(logBlock);
		if (nether && !TreeChopperConfig.isNetherFellingEnabled()) {
			return;
		}

		// A thick multi-log trunk (dark oak/pale oak's 2x2 base, a mega spruce/jungle trunk grown
		// from 4 saplings) still has another log of the same type standing right beside this one
		// at the exact same height while any of its other base logs remain - don't fell yet in
		// that case, so a thick trunk is only felled once that layer is cleared. Only the log that
		// finally clears that whole layer (no matching neighbor left at this height) triggers the
		// fell. A normal single-wide trunk never has a same-height neighbor at all, so this never
		// holds back felling for the common case. Logs the player placed next to the trunk don't
		// count - they are not part of the tree.
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos neighborPos = pos.relative(dir);
			BlockState neighbor = serverLevel.getBlockState(neighborPos);
			if (neighbor.is(BlockTags.LOGS) && sameWoodFamily(logBlock, neighbor.getBlock())
				&& !PlayerPlacedBlocks.isPlaced(serverLevel, neighborPos)) {
				return;
			}
		}

		// This is the AFTER event - `pos` is already air by the time we get here, so it can't be
		// used as a BFS seed itself (it would just fail the "is this block a log" match). Seed
		// with its neighbors instead; only the rest of the connected tree is ours to break here.
		// Some trees place branches diagonally. Acacia uses all diagonal neighbors; mangrove can
		// lean from one level to the next, so include vertical diagonals there while excluding
		// same-level corner links that can join two nearby trunks.
		// The `p.getY() >= pos.getY()` guard is what makes this a directional felling instead of
		// destroying the whole tree regardless of cut height: chopping partway up a thick
		// multi-log trunk (dark oak/pale oak's 2x2 base) should only take down what's above the
		// cut, leaving the stump below standing, exactly like chopping a real tree.
		// Logs the player placed are skipped outright, so a log house or tree house touching the
		// trunk is never pulled into the fell (nor is anything only reachable through it).
		Set<BlockPos> logs = new HashSet<>();
		floodFillLogs(serverLevel, logs, pos, logBlock);

		// Only a real tree gets felled: it must carry a natural canopy (leaves that vanilla itself
		// would decay, i.e. not the persistent ones a player places; wart blocks/shroomlight for
		// the nether). Log cabins from worlds that predate this mod, village houses and bare
		// pillars have none, so they are left alone even when nothing is recorded for them.
		Set<BlockPos> canopy;
		if (nether) {
			canopy = collectNetherCanopy(serverLevel, pos, logs);
		} else {
			canopy = collectLeaves(serverLevel, logs, pos);
			canopy.addAll(collectTreeDecorations(serverLevel, logBlock, pos, logs, canopy));
		}
		if (canopy.isEmpty()) {
			return;
		}

		// A log below the cut that was held up only by the logs being felled (a leaning mangrove or
		// acacia branch, a root-side log) would be left hanging in the air; it goes with the tree.
		Set<BlockPos> orphans = collectOrphanLogs(serverLevel, logs, canopy, pos, logBlock);
		if (!orphans.isEmpty()) {
			logs.addAll(orphans);
			if (!nether) {
				// The canopy of a branch that was left hanging goes with it.
				canopy.addAll(collectLeaves(serverLevel, orphans, pos));
			}
		}

		// Nothing is dropped for a creative player, same as breaking blocks by hand.
		boolean drop = !player.isCreative();
		int logIndex = 0;
		int logStep = effectStep(logs.size(), MAX_LOG_EFFECTS);
		for (BlockPos logPos : logs) {
			destroyBlock(serverLevel, logPos, drop, player, logIndex++ % logStep == 0);
			held.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		}
		if (nether) {
			removeNetherCanopy(serverLevel, held, canopy);
		} else {
			int leafIndex = 0;
			int leafStep = effectStep(canopy.size(), MAX_LEAF_EFFECTS);
			for (BlockPos leafPos : canopy) {
				destroyBlock(serverLevel, leafPos, drop, player, leafIndex++ % leafStep == 0);
			}
		}

		if (TreeChopperConfig.isAutoSaplingPlacement()) {
			replant(serverLevel, player, logBlock, pos, logs);
		}
	}

	// Breadth-first flood fill outward from `seeds`, adding every reachable position matching
	// `matches`. `includeDiagonals` scans all 26 neighbors instead of just the 6 face-adjacent ones.
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

	private static void floodFillLogs(final ServerLevel level, final Set<BlockPos> logs, final BlockPos origin, final Block logBlock) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(logBlock);
		String family = id.getNamespace().equals("minecraft") ? woodFamily(id.getPath()) : "";
		boolean acacia = family.equals("acacia");
		boolean mangrove = family.equals("mangrove");
		Deque<BlockPos> queue = new ArrayDeque<>();
		Predicate<BlockPos> matches = p -> p.getY() >= origin.getY() && level.getBlockState(p).is(BlockTags.LOGS)
			&& sameWoodFamily(logBlock, level.getBlockState(p).getBlock()) && !PlayerPlacedBlocks.isPlaced(level, p)
			&& (!mangrove || !isAnotherMangroveTrunkColumn(level, p, origin, logBlock));
		for (BlockPos seed : logNeighborsOf(origin, acacia, mangrove)) {
			if (logs.size() >= MAX_LOGS) break;
			if (matches.test(seed) && logs.add(seed)) queue.add(seed);
		}
		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			for (BlockPos neighbor : logNeighborsOf(queue.remove(), acacia, mangrove)) {
				if (logs.size() >= MAX_LOGS) break;
				if (matches.test(neighbor) && logs.add(neighbor)) queue.add(neighbor);
			}
		}
	}

	// Logs of the same tree that stay behind below the cut but would float once the felled logs are
	// gone: connected to the cut only, with nothing under any of them. Each group of leftover logs is
	// followed (up to a size that no stump reaches) and kept when any of its logs rests on something
	// solid; only a group with nothing under it at all is added to the felling.
	private static Set<BlockPos> collectOrphanLogs(
		final ServerLevel level, final Set<BlockPos> felled, final Set<BlockPos> canopy, final BlockPos origin, final Block logBlock
	) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(logBlock);
		String family = id.getNamespace().equals("minecraft") ? woodFamily(id.getPath()) : "";
		boolean acacia = family.equals("acacia");
		boolean mangrove = family.equals("mangrove");
		Predicate<BlockPos> candidate = p -> !felled.contains(p) && !p.equals(origin) && level.getBlockState(p).is(BlockTags.LOGS)
			&& sameWoodFamily(logBlock, level.getBlockState(p).getBlock()) && !PlayerPlacedBlocks.isPlaced(level, p);

		Set<BlockPos> orphans = new HashSet<>();
		Set<BlockPos> visited = new HashSet<>();
		List<BlockPos> starts = new ArrayList<>(logNeighborsOf(origin, acacia, mangrove));
		for (BlockPos log : felled) {
			starts.addAll(logNeighborsOf(log, acacia, mangrove));
		}
		for (BlockPos removed : canopy) {
			starts.addAll(neighborsOf(removed, true));
		}
		for (BlockPos start : starts) {
			if (visited.contains(start) || !candidate.test(start)) {
				continue;
			}
			Set<BlockPos> group = new HashSet<>();
			Deque<BlockPos> queue = new ArrayDeque<>();
			group.add(start);
			queue.add(start);
			visited.add(start);
			while (!queue.isEmpty() && group.size() <= MAX_ORPHAN_GROUP) {
				BlockPos current = queue.poll();
				for (BlockPos next : logNeighborsOf(current, acacia, mangrove)) {
					if (candidate.test(next) && group.add(next)) {
						visited.add(next);
						queue.add(next);
					}
				}
			}
			// The group is held up if any of its logs rests on something solid that is not one of its
			// own logs and is not about to be removed with the tree (roots and leaves hold nothing up).
			boolean supported = false;
			for (BlockPos member : group) {
				BlockPos belowPos = member.below();
				BlockState below = level.getBlockState(belowPos);
				if (!group.contains(belowPos) && !below.isAir() && below.getFluidState().isEmpty() && !below.is(BlockTags.LEAVES)
					&& !canopy.contains(belowPos) && !felled.contains(belowPos)) {
					supported = true;
					break;
				}
			}
			if (!supported && group.size() <= MAX_ORPHAN_GROUP) {
				orphans.addAll(group);
			}
		}
		return orphans;
	}

	private static List<BlockPos> logNeighborsOf(final BlockPos pos, final boolean acacia, final boolean mangrove) {
		if (acacia) return neighborsOf(pos, true);
		List<BlockPos> result = new ArrayList<>(mangrove ? 18 : 6);
		for (Direction dir : Direction.values()) result.add(pos.relative(dir));
		if (mangrove) {
			for (int dy : new int[] {-1, 1}) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx != 0 || dz != 0) result.add(pos.offset(dx, dy, dz));
					}
				}
			}
		}
		return result;
	}

	// A diagonal mangrove branch can touch the trunk of a second tree. That tree's own column
	// continues below the cut; the selected tree's elevated branches do not. Keep the other trunk
	// out of the connected set while still allowing its foliage to overlap the selected canopy.
	private static boolean isAnotherMangroveTrunkColumn(
		final ServerLevel level, final BlockPos candidate, final BlockPos origin, final Block logBlock
	) {
		if (candidate.getX() == origin.getX() && candidate.getZ() == origin.getZ()) return false;
		int consecutiveLogs = 0;
		for (int depth = 1; depth <= 8; depth++) {
			BlockPos below = candidate.below(depth);
			if (below.getY() >= origin.getY()) continue;
			BlockState state = level.getBlockState(below);
			if (state.is(BlockTags.LOGS) && sameWoodFamily(logBlock, state.getBlock()) && !PlayerPlacedBlocks.isPlaced(level, below)) {
				if (++consecutiveLogs >= 2) return true;
			} else {
				consecutiveLogs = 0;
			}
		}
		return false;
	}

	// Follow vanilla's leaf-support distance from the selected logs instead of scanning a fixed
	// radius. The computed distance from this tree may be shorter than a stale leaf distance, so
	// accept those leaves while rejecting a leaf whose vanilla distance proves another tree's logs
	// are closer. This also keeps touching canopies from merging merely because their leaves touch.
	private static Set<BlockPos> collectLeaves(
		final ServerLevel level, final Set<BlockPos> logs, final BlockPos originPos
	) {
		Set<BlockPos> leaves = new HashSet<>();
		Deque<LeafNode> queue = new ArrayDeque<>();

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
			if (isNaturalLeaf(state) && LeavesBlock.getOptionalDistanceAt(state).orElse(LeavesBlock.DECAY_DISTANCE) >= 1) {
				leaves.add(seed);
				queue.add(new LeafNode(seed, 1));
			}
		}

		while (!queue.isEmpty() && leaves.size() < MAX_LEAVES) {
			LeafNode current = queue.poll();
			int nextTreeDistance = current.treeDistance() + 1;
			if (nextTreeDistance > LeavesBlock.DECAY_DISTANCE) {
				continue;
			}
			for (BlockPos neighbor : neighborsOf(current.pos(), true)) {
				if (leaves.size() >= MAX_LEAVES || leaves.contains(neighbor) || neighbor.getY() < originPos.getY()) {
					continue;
				}
				BlockState state = level.getBlockState(neighbor);
				if (!isNaturalLeaf(state)) {
					continue;
				}
				OptionalInt neighborDistance = LeavesBlock.getOptionalDistanceAt(state);
				if (neighborDistance.isPresent() && neighborDistance.getAsInt() >= nextTreeDistance) {
					leaves.add(neighbor);
					queue.add(new LeafNode(neighbor, nextTreeDistance));
				}
			}
		}
		return leaves;
	}

	// Leaves vanilla itself would decay once unsupported. Placed leaves are persistent, so a
	// player's hedge or roof touching a tree is neither felled nor mistaken for its canopy.
	private static boolean isNaturalLeaf(final BlockState state) {
		return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
	}

	// Collect blocks that hang from a canopy but aren't leaves themselves. Player-placed blocks are
	// skipped; muddy mangrove roots deliberately don't match the plain-root check.
	private static Set<BlockPos> collectTreeDecorations(
		final ServerLevel level, final Block logBlock, final BlockPos origin, final Set<BlockPos> logs, final Set<BlockPos> leaves
	) {
		Identifier logId = BuiltInRegistries.BLOCK.getKey(logBlock);
		boolean mangrove = logId.getNamespace().equals("minecraft") && woodFamily(logId.getPath()).equals("mangrove");
		Set<BlockPos> decorations = new HashSet<>();
		List<BlockPos> seeds = new ArrayList<>();
		seeds.addAll(neighborsOf(origin, true));
		for (BlockPos log : logs) {
			seeds.addAll(neighborsOf(log, true));
		}
		for (BlockPos leaf : leaves) {
			seeds.addAll(neighborsOf(leaf, true));
		}
		floodFill(
			level, decorations, seeds,
			p -> isTreeDecoration(level.getBlockState(p), mangrove) && !PlayerPlacedBlocks.isPlaced(level, p),
			true, MAX_TREE_DECORATIONS
		);
		return decorations;
	}

	private static boolean isTreeDecoration(final BlockState state, final boolean mangrove) {
		return state.is(Blocks.VINE) || state.is(Blocks.PALE_HANGING_MOSS)
			|| (mangrove && (state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MANGROVE_PROPAGULE)));
	}

	private static boolean usesDiagonalLogConnections(final Block logBlock) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(logBlock);
		return id.getNamespace().equals("minecraft") && woodFamily(id.getPath()).equals("acacia");
	}

	private record LeafNode(BlockPos pos, int treeDistance) {}

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

	private static boolean isNetherWood(final Block block) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		if (!id.getNamespace().equals("minecraft")) return false;
		String family = woodFamily(id.getPath());
		return family.equals("crimson") || family.equals("warped");
	}

	// Wart blocks and shroomlight connected to the felled stems, skipping any a player placed.
	private static Set<BlockPos> collectNetherCanopy(final ServerLevel level, final BlockPos origin, final Set<BlockPos> logs) {
		Set<BlockPos> canopy = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		List<BlockPos> seeds = new ArrayList<>(neighborsOf(origin, true));
		for (BlockPos log : logs) seeds.addAll(neighborsOf(log, true));
		for (BlockPos seed : seeds) {
			if (canopy.size() < MAX_NETHER_CANOPY && isNaturalNetherCanopy(level, seed) && canopy.add(seed)) queue.add(seed);
		}
		while (!queue.isEmpty() && canopy.size() < MAX_NETHER_CANOPY) {
			for (BlockPos next : neighborsOf(queue.remove(), true)) {
				if (canopy.size() >= MAX_NETHER_CANOPY) break;
				if (isNaturalNetherCanopy(level, next) && canopy.add(next)) queue.add(next);
			}
		}
		return canopy;
	}

	private static boolean isNaturalNetherCanopy(final ServerLevel level, final BlockPos pos) {
		return PlayerPlacedBlocks.isNetherCanopy(level.getBlockState(pos)) && !PlayerPlacedBlocks.isPlaced(level, pos);
	}

	private static void removeNetherCanopy(final ServerLevel level, final ItemStack axe, final Set<BlockPos> canopy) {
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

	private static int fortuneLevel(ServerLevel level, ItemStack stack) {
		Holder<Enchantment> fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
		return EnchantmentHelper.getItemEnchantmentLevel(fortune, stack);
	}

	// Breaks a block like ServerLevel.destroyBlock does (drops, block removal, game event) but only
	// plays the break sound and particles when `effects` is set - see MAX_LOG_EFFECTS.
	private static void destroyBlock(final ServerLevel level, final BlockPos pos, final boolean drop, final Player player, final boolean effects) {
		if (effects) {
			level.destroyBlock(pos, drop, player, 512);
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		if (drop) {
			Block.dropResources(state, level, pos, state.hasBlockEntity() ? level.getBlockEntity(pos) : null, player, ItemStack.EMPTY);
		}
		if (level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), Block.UPDATE_ALL, 512)) {
			level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
		}
	}

	// Distance between the blocks that keep their sound: 1 (every block) while the count is at or
	// under the cap, then spread evenly.
	private static int effectStep(final int blocks, final int maxEffects) {
		return Math.max(1, (blocks + maxEffects - 1) / maxEffects);
	}

	// Puts the felled tree's own sapling (fungus, propagule) back where its trunk stood, taken from
	// the player's inventory: nothing is planted for a player who carries none. It only happens once
	// the whole trunk is gone: the ground under the cut must still accept the sapling, which a
	// remaining stump (or a cut made partway up) never does. Dark oak and pale oak only grow from a
	// 2x2 of saplings, and spruce/jungle grow their mega form from one, so those follow the
	// footprint of the 2x2 trunk that was just removed (a spruce or jungle falls back to a single
	// sapling when the player carries fewer than four); every other tree gets a single sapling.
	// Either all the saplings of a tree go down or none do.
	private static void replant(final ServerLevel level, final Player player, final Block logBlock, final BlockPos origin, final Set<BlockPos> logs) {
		Identifier logId = BuiltInRegistries.BLOCK.getKey(logBlock);
		if (!logId.getNamespace().equals("minecraft")) return;
		String family = woodFamily(logId.getPath());
		Block sapling = saplingFor(family);
		if (sapling == null) return;

		List<BlockPos> square = trunkSquare(level, origin, logs);
		boolean needsFour = family.equals("dark_oak") || family.equals("pale_oak");
		boolean mayBeMega = family.equals("spruce") || family.equals("jungle");
		List<BlockPos> targets;
		List<BlockPos> fallback = null;
		if (square != null && (needsFour || mayBeMega)) {
			targets = square;
			if (mayBeMega) {
				fallback = List.of(origin);
			}
		} else if (needsFour) {
			return;
		} else {
			targets = List.of(origin);
		}

		// Not planted right away: the swing that just felled the tree is usually still going, and
		// a sapling pops instantly, so the very next hit would knock it out again. It is planted a
		// moment later instead, once the chopping is over - and only then taken from the inventory.
		PENDING_SAPLINGS.add(new PendingSapling(
			level, player.getUUID(), sapling, targets, fallback, tickCounter + SAPLING_DELAY_TICKS
		));
	}

	private static int countItem(final Player player, final Item item) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void takeItem(final Player player, final Item item, final int amount) {
		int remaining = amount;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) {
				int taken = Math.min(stack.getCount(), remaining);
				stack.shrink(taken);
				remaining -= taken;
			}
		}
		player.getInventory().setChanged();
	}

	private static boolean canPlant(final ServerLevel level, final List<BlockPos> targets, final BlockState saplingState) {
		for (BlockPos target : targets) {
			BlockState current = level.getBlockState(target);
			if (!level.isLoaded(target) || !(current.isAir() || current.canBeReplaced()) || !saplingState.canSurvive(level, target)) {
				return false;
			}
		}
		return true;
	}

	// Plants the saplings that have waited out their delay. The spot is checked again first: the
	// player may have built or planted something there in the meantime, which then wins.
	private static void tickSaplings() {
		tickCounter++;
		for (Iterator<PendingSapling> iterator = PENDING_SAPLINGS.iterator(); iterator.hasNext();) {
			PendingSapling pending = iterator.next();
			if (tickCounter < pending.dueTick()) {
				continue;
			}
			iterator.remove();
			plant(pending);
		}
	}

	// Plants the sapling(s) of one felled tree, if the player still carries enough of them and is
	// still in the same level. Creative players keep their items, like placing blocks by hand.
	private static void plant(final PendingSapling pending) {
		ServerLevel level = pending.level();
		Player player = level.getServer().getPlayerList().getPlayer(pending.playerId());
		if (player == null || player.level() != level) {
			return;
		}

		Item item = pending.sapling().asItem();
		BlockState state = pending.sapling().defaultBlockState();
		int carried = countItem(player, item);
		List<BlockPos> chosen = null;
		if (carried >= pending.targets().size() && canPlant(level, pending.targets(), state)) {
			chosen = pending.targets();
		} else if (pending.fallback() != null && carried >= pending.fallback().size() && canPlant(level, pending.fallback(), state)) {
			chosen = pending.fallback();
		}
		if (chosen == null) {
			return;
		}

		if (!player.isCreative()) {
			takeItem(player, item, chosen.size());
		}
		for (BlockPos target : chosen) {
			level.setBlock(target, state, Block.UPDATE_ALL);
		}
	}

	private record PendingSapling(
		ServerLevel level, UUID playerId, Block sapling, List<BlockPos> targets, @Nullable List<BlockPos> fallback, long dueTick
	) {}

	private static Block saplingFor(final String family) {
		return switch (family) {
			case "oak" -> Blocks.OAK_SAPLING;
			case "spruce" -> Blocks.SPRUCE_SAPLING;
			case "birch" -> Blocks.BIRCH_SAPLING;
			case "jungle" -> Blocks.JUNGLE_SAPLING;
			case "acacia" -> Blocks.ACACIA_SAPLING;
			case "dark_oak" -> Blocks.DARK_OAK_SAPLING;
			case "pale_oak" -> Blocks.PALE_OAK_SAPLING;
			case "cherry" -> Blocks.CHERRY_SAPLING;
			case "poplar" -> Blocks.POPLAR_SAPLING;
			case "mangrove" -> Blocks.MANGROVE_PROPAGULE;
			case "crimson" -> Blocks.CRIMSON_FUNGUS;
			case "warped" -> Blocks.WARPED_FUNGUS;
			default -> null;
		};
	}

	// The 2x2 footprint of a thick trunk just felled, as the four ground columns, or null for a
	// normal one-wide trunk. The felled trunk continues straight up from all four columns (the
	// layer above the cut is part of `logs`), and the other three base logs were already broken
	// to get here, so they are free again.
	private static List<BlockPos> trunkSquare(final ServerLevel level, final BlockPos origin, final Set<BlockPos> logs) {
		for (int dx = -1; dx <= 0; dx++) {
			for (int dz = -1; dz <= 0; dz++) {
				List<BlockPos> square = List.of(
					origin.offset(dx, 0, dz), origin.offset(dx + 1, 0, dz), origin.offset(dx, 0, dz + 1), origin.offset(dx + 1, 0, dz + 1)
				);
				boolean thick = true;
				for (BlockPos column : square) {
					if (!logs.contains(column.above()) || !(column.equals(origin) || level.getBlockState(column).isAir())) {
						thick = false;
						break;
					}
				}
				if (thick) return square;
			}
		}
		return null;
	}
}
