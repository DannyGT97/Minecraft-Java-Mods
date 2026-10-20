package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.ui.HotbarTools;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

// The wheel changes the flying speed (or, with the tools on the hotbar, the tool) while the free camera is on, instead of the hotbar slot.
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void holoprint$scroll(final long window, final double xOffset, final double yOffset, final CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (Freecam.isActive() && minecraft.gui.screen() == null) {
			// With the tools on the hotbar the wheel picks the tool; Ctrl + wheel (or the speed keys) change the speed.
			if (HotbarTools.enabled() && !minecraft.hasControlDown()) {
				HotbarTools.scroll(minecraft, yOffset);
			} else {
				Freecam.changeSpeed(yOffset);
			}
			ci.cancel();
		}
	}
}
