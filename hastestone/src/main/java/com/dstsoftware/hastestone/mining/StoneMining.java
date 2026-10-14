package com.dstsoftware.hastestone.mining;

import com.dstsoftware.hastestone.config.HastestoneConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mining progress per tick for deepslate and cobblestone, measured against plain stone: the player's
 * real stone progress with the tool in hand is the reference, so every pickaxe, enchantment and
 * effect behaves exactly as it does on stone.
 */
public final class StoneMining {
	private static final int HASTE_II = 1;

	private StoneMining() {}

	/**
	 * @param vanilla the progress vanilla computed for {@code state}
	 * @return the progress to use instead (never lower than vanilla's)
	 */
	public static float adjust(final float vanilla, final BlockState state, final Player player, final BlockGetter level, final BlockPos pos) {
		if (vanilla >= 1.0F || !HastestoneConfig.isEnabled()) {
			return vanilla;
		}
		boolean deepslate = state.is(Blocks.DEEPSLATE);
		boolean cobblestone = state.is(Blocks.COBBLESTONE);
		if (!deepslate && !cobblestone) {
			return vanilla;
		}

		float stone = Blocks.STONE.defaultBlockState().getDestroyProgress(player, level, pos);
		// Deepslate takes exactly as long as stone does with this tool: stone's own progress, which
		// already accounts for the pickaxe type, Efficiency, Haste and the rest.
		if (deepslate && HastestoneConfig.isDeepslateAtStoneSpeed()) {
			return Math.max(vanilla, stone);
		}
		// With Haste II, anything that would break stone at once breaks these at once too.
		boolean allowed = deepslate ? HastestoneConfig.isDeepslateInstantWithHaste2() : HastestoneConfig.isCobblestoneInstantWithHaste2();
		if (allowed && stone >= 1.0F && hasHasteTwo(player)) {
			return stone;
		}
		return vanilla;
	}

	private static boolean hasHasteTwo(final Player player) {
		MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
		return haste != null && haste.getAmplifier() >= HASTE_II;
	}
}
