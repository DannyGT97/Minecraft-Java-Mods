package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.freecam.Freecam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ContextualBar;

// The experience bar (and its level number) belongs to the player, not to a camera flying around, so it
// is not drawn while the free camera is on. The hotbar and the hearts stay as they are.
@Mixin(Hud.class)
public abstract class HudMixin {
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
