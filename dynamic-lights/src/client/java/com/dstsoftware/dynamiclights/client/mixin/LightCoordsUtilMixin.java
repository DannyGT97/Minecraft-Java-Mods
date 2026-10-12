package com.dstsoftware.dynamiclights.client.mixin;

import com.dstsoftware.dynamiclights.client.light.DynamicLights;

import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Blends the dynamic lights into vanilla's per-block lightmap calculation.
// LightCoordsUtil.getLightCoords(BrightnessGetter, ...) is the single point every terrain lighting
// path funnels through (both the ambient-occlusion and the flat path of BlockModelLighter call it),
// and the 2-arg overload delegates into this 4-arg one, so hooking it covers both.
@Mixin(LightCoordsUtil.class)
public abstract class LightCoordsUtilMixin {
	@Inject(
		method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
		at = @At("RETURN"),
		cancellable = true
	)
	private static void dynamiclights$applyDynamicLight(
		final LightCoordsUtil.BrightnessGetter brightnessGetter,
		final BlockAndLightGetter level,
		final BlockState state,
		final BlockPos pos,
		final CallbackInfoReturnable<Integer> cir
	) {
		int dynamic = DynamicLights.getDynamicLightLevel(pos);
		if (dynamic <= 0) {
			return;
		}
		cir.setReturnValue(LightCoordsUtil.max(cir.getReturnValueI(), LightCoordsUtil.pack(dynamic, 0)));
	}
}
