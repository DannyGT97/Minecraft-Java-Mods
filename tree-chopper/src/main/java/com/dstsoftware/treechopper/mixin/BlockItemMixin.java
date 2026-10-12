package com.dstsoftware.treechopper.mixin;

import com.dstsoftware.treechopper.tree.PlayerPlacedBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	// Every block placed from an item (player, dispenser, ...) goes through here once the world
	// accepted it; record the log-like ones so felling can tell them apart from natural trees.
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void treechopper$trackPlaced(
		final BlockPlaceContext context, final BlockState state, final CallbackInfoReturnable<Boolean> cir
	) {
		if (cir.getReturnValueZ() && !context.getLevel().isClientSide() && PlayerPlacedBlocks.shouldTrack(state)) {
			PlayerPlacedBlocks.mark(context.getLevel(), context.getClickedPos());
		}
	}
}
