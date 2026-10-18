package com.dannygt97.gameplaytweaks.client.mixin;

import com.dannygt97.gameplaytweaks.client.config.GameplayTweaksConfig;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies fullbright to the exact render state uploaded to the lightmap shader. */
@Mixin(Lightmap.class)
public abstract class LightmapRenderMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void gameplaytweaks$forceFullbright(LightmapRenderState state, CallbackInfo ci) {
		if (!GameplayTweaksConfig.isFullbrightEnabled()) return;
		state.needsUpdate = true;
		state.blockFactor = 0.0F;
		state.skyFactor = 0.0F;
		state.nightVisionEffectIntensity = 0.0F;
		state.darknessEffectScale = 0.0F;
		state.bossOverlayWorldDarkening = 0.0F;
		state.brightness = 0.0F;
		state.ambientColor = LightmapRenderStateExtractor.WHITE;
		state.blockLightTint = LightmapRenderStateExtractor.WHITE;
		state.skyLightColor = LightmapRenderStateExtractor.WHITE;
		state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
	}
}
