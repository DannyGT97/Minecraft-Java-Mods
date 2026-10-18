package com.dannygt97.gameplaytweaks.client.mixin;

import com.dannygt97.gameplaytweaks.client.config.GameplayTweaksConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void gameplaytweaks$disableDimensionFog(Camera camera, int viewDistance, DeltaTracker deltaTracker,
			float partialTick, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		if (camera.getFluidInCamera() != FogType.NONE) return;

		String dimension = level.dimension().equals(Level.OVERWORLD) ? "overworld"
			: level.dimension().equals(Level.NETHER) ? "nether"
			: level.dimension().equals(Level.END) ? "end" : "";
		if (dimension.isEmpty() || !GameplayTweaksConfig.isFogDisabled(dimension)) return;

		FogData fog = cir.getReturnValue();
		fog.environmentalStart = 1_000_000.0F;
		fog.environmentalEnd = 1_000_001.0F;
	}
}
