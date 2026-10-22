package com.dannygt97.hastemine.mixin;

import com.dannygt97.hastemine.mining.StoneMining;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

// Both sides ask the block state how far a tick of mining gets: the client to predict an instant
// break and to animate the crack, the server to decide it (a start-of-mining action breaks the
// block at once when the progress is 1 or more). Adjusting the answer here changes both.
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
	@Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
	private void hastemine$adjustProgress(final Player player, final BlockGetter level, final BlockPos pos, final CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(StoneMining.adjust(cir.getReturnValueF(), (BlockState) (Object) this, player, level, pos));
	}
}
