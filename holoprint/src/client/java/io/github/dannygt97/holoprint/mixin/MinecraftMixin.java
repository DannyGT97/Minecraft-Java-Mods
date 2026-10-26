package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.placement.Editor;
import io.github.dannygt97.holoprint.placement.EasyPlace;
import io.github.dannygt97.holoprint.ui.HotbarTools;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

// Early priority (the default is 1000): the use click goes through the easy placement first, so a mod that places blocks where the pointer misses (reach-around
// bridging) sees the click the placement has already dealt with.
// While the free camera is on, the player must not attack, use items or pick blocks: those clicks
// belong to the hologram instead.
@Mixin(value = Minecraft.class, priority = 500)
public abstract class MinecraftMixin {
	// The number keys pick a tool of the editor instead of a slot of the player (see HotbarTools).
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void holoprint$hotbarKeys(final CallbackInfo ci) {
		HotbarTools.consumeKeys(Minecraft.getInstance());
		Editor.dropGameKeys(Minecraft.getInstance());
	}

	// Escape cancels what the editor is doing (a move, a selection, a copy in hand) before it opens the pause menu.
	@Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
	private void holoprint$escape(final boolean pauseOnly, final CallbackInfo ci) {
		if (Editor.escape(Minecraft.getInstance())) {
			ci.cancel();
		}
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void holoprint$startAttack(final CallbackInfoReturnable<Boolean> cir) {
		if (Freecam.isActive()) {
			if (!HotbarTools.click(Minecraft.getInstance())) {
				Editor.onPrimaryClick();
			}
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
			if (!HotbarTools.click(Minecraft.getInstance())) {
				Editor.onSecondaryClick();
			}
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
