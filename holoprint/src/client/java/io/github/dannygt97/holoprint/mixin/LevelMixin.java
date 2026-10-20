package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.hologram.Verifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// Every change of a block in the client's world (placed, broken, opened, updated by a neighbour) reaches the hologram checker
// at once, so what the hologram hides or outlines never lags behind the world.
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
	private void holoprint$blockChanged(final BlockPos pos, final BlockState state, final int flags, final int depth, final CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && ((Level) (Object) this).isClientSide()) {
			Verifier.blockChanged(pos, state);
		}
	}
}
