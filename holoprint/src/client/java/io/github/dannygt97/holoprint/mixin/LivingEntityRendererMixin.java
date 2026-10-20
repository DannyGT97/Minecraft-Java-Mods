package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.hologram.GhostEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;

// A living entity that is invisible but shown anyway is drawn translucent with a fixed 15% white. The
// entities of a hologram use that same path, with the hologram's own colour and opacity instead.
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@ModifyConstant(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		constant = @Constant(intValue = 654311423))
	private int holoprint$hologramColor(final int original) {
		int ghost = GhostEntities.ghostColor;
		return ghost != 0 ? ghost : original;
	}
}
