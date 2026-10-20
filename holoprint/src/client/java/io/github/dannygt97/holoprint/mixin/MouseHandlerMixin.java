package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.freecam.Freecam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

// The wheel changes the flying speed while the free camera is on, instead of the hotbar slot.
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void holoprint$scroll(final long window, final double xOffset, final double yOffset, final CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (Freecam.isActive() && minecraft.gui.screen() == null) {
			Freecam.changeSpeed(yOffset);
			ci.cancel();
		}
	}
}
