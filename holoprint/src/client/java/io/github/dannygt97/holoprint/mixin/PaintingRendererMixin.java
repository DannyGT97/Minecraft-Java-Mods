package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.hologram.GhostEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

// A painting of a hologram is drawn translucent like everything else of it: its own translucent render type, and the colour of the hologram
// instead of white on every vertex. The vertices are written later, when the frame is drawn, so the colour is carried inside the geometry call.
@Mixin(PaintingRenderer.class)
public abstract class PaintingRendererMixin {
	@Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entitySolidZOffsetForward(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
	private RenderType holoprint$translucent(final Identifier texture) {
		return GhostEntities.ghostColor != 0 ? RenderTypes.entityTranslucent(texture) : RenderTypes.entitySolidZOffsetForward(texture);
	}

	@ModifyArg(method = "renderPainting", index = 2,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"))
	private SubmitNodeCollector.CustomGeometryRenderer holoprint$carryColor(final SubmitNodeCollector.CustomGeometryRenderer original) {
		final int color = GhostEntities.ghostColor;
		if (color == 0) {
			return original;
		}
		return (pose, consumer) -> {
			int before = GhostEntities.paintingColor;
			GhostEntities.paintingColor = color;
			try {
				original.render(pose, consumer);
			} finally {
				GhostEntities.paintingColor = before;
			}
		};
	}

	@ModifyConstant(method = "vertex", constant = @Constant(intValue = -1))
	private static int holoprint$vertexColor(final int original) {
		int color = GhostEntities.paintingColor;
		return color != 0 ? color : original;
	}
}
