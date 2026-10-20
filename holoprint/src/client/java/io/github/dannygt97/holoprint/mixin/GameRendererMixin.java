package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.freecam.Freecam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

// The player's own hand and held item belong to a first-person view, so they are not drawn while
// the camera is flying somewhere else.
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void holoprint$hideHand(final CallbackInfo ci) {
		if (Freecam.isActive()) {
			ci.cancel();
		}
	}
}
