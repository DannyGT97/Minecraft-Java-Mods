package com.dannygt97.gameplaytweaks.client;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Finds the first replaceable voxel along the player's view ray with a solid face behind it. */
public final class ReachAroundPlacement {
	private static final double STEP = 0.05D;
	private static final double MIN_DISTANCE = 0.35D;
	private static BlockHitResult lastTickTarget;

	private ReachAroundPlacement() {}

	public static void tick(Minecraft minecraft) {
		lastTickTarget = null;
		if (!GameplayTweaksState.enabled() || minecraft.player == null || minecraft.level == null
				|| minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.MISS) return;
		ItemStack stack = minecraft.player.getMainHandItem().getItem() instanceof BlockItem
			? minecraft.player.getMainHandItem() : minecraft.player.getOffhandItem();
		if (stack.getItem() instanceof BlockItem) lastTickTarget = findPlacement(minecraft, stack);
	}

	public static BlockHitResult getLastTickTarget() {
		return lastTickTarget;
	}

	private static BlockHitResult findPlacement(Minecraft minecraft, ItemStack stack) {
		Player player = minecraft.player;
		if (!GameplayTweaksState.enabled() || player == null || minecraft.level == null
				|| !(stack.getItem() instanceof BlockItem)) return null;

		Vec3 start = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F).normalize();
		double reach = player.blockInteractionRange();
		Set<BlockPos> checked = new HashSet<>();

		// Traverse the camera ray in near-to-far order, as BridgingMod does with
		// a voxel path, and accept only a face which points back toward the view.
		for (double distance = MIN_DISTANCE; distance <= reach; distance += STEP) {
			Vec3 point = start.add(look.scale(distance));
			BlockPos target = BlockPos.containing(point.x, point.y, point.z);
			if (!checked.add(target) || !minecraft.level.getBlockState(target).canBeReplaced()) continue;
			if (player.getBoundingBox().intersects(target.getX(), target.getY(), target.getZ(),
				target.getX() + 1.0D, target.getY() + 1.0D, target.getZ() + 1.0D)) continue;

			for (Direction towardView : Direction.values()) {
				if (look.dot(Vec3.atLowerCornerOf(towardView.getOpposite().getUnitVec3i())) <= 0.1D) continue;
				BlockPos supportPos = target.relative(towardView);
				Direction hitFace = towardView.getOpposite();
				if (!canPlaceAt(minecraft, player, stack, supportPos, target, hitFace)) continue;
				if (HoloprintCompat.owns(target)) return null;
				return makeHit(target, hitFace);
			}
		}
		return null;
	}

	private static boolean canPlaceAt(Minecraft minecraft, Player player, ItemStack stack,
			BlockPos supportPos, BlockPos target, Direction hitFace) {
		var targetState = minecraft.level.getBlockState(target);
		var support = minecraft.level.getBlockState(supportPos);
		return targetState.canBeReplaced()
			&& !player.getBoundingBox().intersects(target.getX(), target.getY(), target.getZ(),
				target.getX() + 1.0D, target.getY() + 1.0D, target.getZ() + 1.0D)
			&& !support.isAir()
			&& !(support.getBlock() instanceof LiquidBlock)
			&& !support.canBeReplaced()
			&& !support.getCollisionShape(minecraft.level, supportPos).isEmpty()
			&& player.mayUseItemAt(target, hitFace, stack);
	}

	private static BlockHitResult makeHit(BlockPos target, Direction hitFace) {
		// BridgingMod's default target marks the placement voxel itself as the
		// hit block and uses inside=true so BlockPlaceContext resolves the
		// intended support face without requiring a visible face raycast.
		return new BlockHitResult(Vec3.atCenterOf(target), hitFace, target, true);
	}
}
