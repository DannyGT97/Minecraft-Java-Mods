package com.dannygt97.hastemine.mining;

import com.dannygt97.hastemine.config.HastemineConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mining progress per tick for deepslate and cobblestone, measured against plain stone: the player's
 * real stone progress with the tool in hand is the reference, so every pickaxe, enchantment and
 * effect behaves exactly as it does on stone. Overworld logs instead get Bedrock Edition's extra
 * Haste scaling on top of vanilla's.
 */
public final class StoneMining {
	private static final int HASTE_II = 1;

	private StoneMining() {}

	/**
	 * @param vanilla the progress vanilla computed for {@code state}
	 * @return the progress to use instead (never lower than vanilla's)
	 */
	public static float adjust(final float vanilla, final BlockState state, final Player player, final BlockGetter level, final BlockPos pos) {
		if (vanilla >= 1.0F || !HastemineConfig.isEnabled()) {
			return vanilla;
		}
		if (isOverworldLog(state)) {
			// With Haste II and an axe, logs go at once (when the option is on); otherwise Bedrock's extra Haste scaling may still apply.
			if (HastemineConfig.isLogsInstantWithHaste2() && hasHasteTwo(player) && player.getMainHandItem().is(ItemTags.AXES)) {
				return 1.0F;
			}
			return HastemineConfig.isLogsBedrockHaste() ? vanilla * bedrockHasteBonus(player) : vanilla;
		}
		if (HastemineConfig.isSlabsInstantWithHaste2() && state.is(BlockTags.SLABS) && state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			// Any slab broken with a pickaxe (not the wooden ones) goes at once with Haste II whenever stone would with this tool.
			float stone = Blocks.STONE.defaultBlockState().getDestroyProgress(player, level, pos);
			return stone >= 1.0F && hasHasteTwo(player) ? stone : vanilla;
		}
		boolean deepslate = state.is(Blocks.DEEPSLATE);
		boolean cobblestone = state.is(Blocks.COBBLESTONE);
		if (!deepslate && !cobblestone) {
			return vanilla;
		}

		float stone = Blocks.STONE.defaultBlockState().getDestroyProgress(player, level, pos);
		// Deepslate takes exactly as long as stone does with this tool: stone's own progress, which
		// already accounts for the pickaxe type, Efficiency, Haste and the rest.
		if (deepslate && HastemineConfig.isDeepslateAtStoneSpeed()) {
			return Math.max(vanilla, stone);
		}
		// With Haste II, anything that would break stone at once breaks these at once too.
		boolean allowed = deepslate ? HastemineConfig.isDeepslateInstantWithHaste2() : HastemineConfig.isCobblestoneInstantWithHaste2();
		if (allowed && stone >= 1.0F && hasHasteTwo(player)) {
			return stone;
		}
		return vanilla;
	}

	/** Logs and stripped logs of the Overworld trees; wood blocks, Nether stems and bamboo are left alone. */
	private static boolean isOverworldLog(final BlockState state) {
		if (!state.is(BlockTags.LOGS)) {
			return false;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id.getNamespace().equals("minecraft") && id.getPath().endsWith("_log");
	}

	/**
	 * What Bedrock Edition adds to Java's Haste speed-up of (1 + 0.2n): its mining progress is also
	 * multiplied by 1.2^n for Haste level n, so Haste II mines about 2.02x as fast there instead of
	 * 1.4x. It scales with every level, beacon or potion. (Bedrock 26.70 later made it match Java.)
	 */
	private static float bedrockHasteBonus(final Player player) {
		MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
		return haste == null ? 1.0F : (float) Math.pow(1.2D, haste.getAmplifier() + 1);
	}

	private static boolean hasHasteTwo(final Player player) {
		MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
		return haste != null && haste.getAmplifier() >= HASTE_II;
	}
}
