package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.freecam.Freecam;
import io.github.dannygt97.holoprint.ui.HotbarTools;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ContextualBar;

// While the free camera is on, the hotbar shows the editor's tools (see HotbarTools).
// The experience bar (and its level number) belongs to the player, not to a camera flying around, so it
// is not drawn while the free camera is on. The hotbar and the hearts stay as they are.
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
	private void holoprint$hotbar(final GuiGraphicsExtractor graphics, final DeltaTracker delta, final CallbackInfo ci) {
		if (HotbarTools.render(graphics)) {
			ci.cancel();
		}
	}

	// The name of the item in the player's slot (the vanilla note above the hotbar) means nothing while the hotbar shows the tools: moving along them
	// changes the slot a controller keeps, and every move would flash the name of what happens to be there.
	@Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
	private void holoprint$itemName(final GuiGraphicsExtractor graphics, final CallbackInfo ci) {
		if (HotbarTools.enabled()) {
			ci.cancel();
		}
	}

	@Redirect(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void holoprint$barBackground(final ContextualBar bar, final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		if (!Freecam.isActive()) {
			bar.extractBackground(graphics, delta);
		}
	}

	@Redirect(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void holoprint$barContents(final ContextualBar bar, final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		if (!Freecam.isActive()) {
			bar.extractRenderState(graphics, delta);
		}
	}

	@Redirect(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
	private void holoprint$level(final GuiGraphicsExtractor graphics, final Font font, final int level) {
		if (!Freecam.isActive()) {
			ContextualBar.extractExperienceLevel(graphics, font, level);
		}
	}
}
