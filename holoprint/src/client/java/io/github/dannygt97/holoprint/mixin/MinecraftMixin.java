package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.placement.Editor;
import io.github.dannygt97.holoprint.placement.EasyPlace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

// While the free camera is on, the player must not attack, use items or pick blocks: those clicks
// belong to the hologram instead.
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void holoprint$startAttack(final CallbackInfoReturnable<Boolean> cir) {
		if (Freecam.isActive()) {
			Editor.onPrimaryClick();
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void holoprint$continueAttack(final boolean down, final CallbackInfo ci) {
		if (Freecam.isActive()) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void holoprint$startUseItem(final CallbackInfo ci) {
		if (Freecam.isActive()) {
			Editor.onSecondaryClick();
			ci.cancel();
		} else if (EasyPlace.before(Minecraft.getInstance())) {
			ci.cancel();
		}
	}

	// Gives the player's rotation back when the click needed it turned to place a block in the right state.
	@Inject(method = "startUseItem", at = @At("RETURN"))
	private void holoprint$afterUseItem(final CallbackInfo ci) {
		EasyPlace.after(Minecraft.getInstance());
	}

	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void holoprint$pickBlock(final CallbackInfo ci) {
		if (Freecam.isActive()) {
			ci.cancel();
		}
	}
}
