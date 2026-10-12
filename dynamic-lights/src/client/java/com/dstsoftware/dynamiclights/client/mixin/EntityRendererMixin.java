package com.dstsoftware.dynamiclights.client.mixin;

import com.dstsoftware.dynamiclights.client.light.DynamicLights;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Same blend as LightCoordsUtilMixin but for entities. EntityRenderer.getPackedLightCoords is a
// separate code path from terrain - it reads level.getBrightness directly and never calls
// LightCoordsUtil.getLightCoords - so without this second hook entities (including the player
// holding the torch) would stay dark even once the terrain around them is lit.
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
	private void dynamiclights$applyDynamicLight(final Entity entity, final float partialTickTime, final CallbackInfoReturnable<Integer> cir) {
		// The continuous, interpolated position matches where the entity is drawn this frame;
		// entity.blockPosition() would jump by a whole block each time a boundary is crossed.
		int dynamic = DynamicLights.getDynamicLightLevel(entity.getPosition(partialTickTime));
		if (dynamic <= 0) {
			return;
		}
		cir.setReturnValue(LightCoordsUtil.max(cir.getReturnValueI(), LightCoordsUtil.pack(dynamic, 0)));
	}
}
