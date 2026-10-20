package io.github.dannygt97.holoprint.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Lets the easy placement ask a block item which state it would place, without placing anything. */
@Mixin(BlockItem.class)
public interface BlockItemAccessor {
	@Invoker("getPlacementState")
	BlockState holoprint$placementState(BlockPlaceContext context);

	@Invoker("canPlace")
	boolean holoprint$canPlace(BlockPlaceContext context, BlockState state);
}
