package com.dannygt97.gameplaytweaks.client.mixin;

import com.dannygt97.gameplaytweaks.client.config.GameplayTweaksConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
	@Shadow private boolean needsUpdate;
	@Unique private boolean gameplaytweaks$wasFullbright;
	@Unique private boolean gameplaytweaks$initialized;

	@Inject(method = "extract", at = @At("HEAD"))
	private void gameplaytweaks$refreshWhenToggled(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		boolean enabled = GameplayTweaksConfig.isFullbrightEnabled();
		if (!gameplaytweaks$initialized || enabled != gameplaytweaks$wasFullbright) {
			needsUpdate = true;
			gameplaytweaks$wasFullbright = enabled;
			gameplaytweaks$initialized = true;
		}
	}

	@Inject(method = "extract", at = @At("TAIL"))
	private void gameplaytweaks$forceWhiteLightmap(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		if (!GameplayTweaksConfig.isFullbrightEnabled()) return;
		state.blockFactor = 0.0F;
		state.skyFactor = 0.0F;
		state.nightVisionEffectIntensity = 0.0F;
		state.darknessEffectScale = 0.0F;
		state.bossOverlayWorldDarkening = 0.0F;
		state.brightness = 0.0F;
		state.ambientColor = LightmapRenderStateExtractor.WHITE;
	}
}