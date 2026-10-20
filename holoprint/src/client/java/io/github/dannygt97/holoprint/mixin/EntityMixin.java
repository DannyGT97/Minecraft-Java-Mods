package io.github.dannygt97.holoprint.mixin;

import io.github.dannygt97.holoprint.freecam.Freecam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

// The mouse (and any controller mod that turns the player the same way) looks around through
// Entity.turn. While the free camera is on, that turn belongs to the camera, not to the player.
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void holoprint$turnFreecam(final double yawDelta, final double pitchDelta, final CallbackInfo ci) {
		if (Freecam.isActive() && (Object) this == Minecraft.getInstance().player) {
			Freecam.turn(yawDelta, pitchDelta);
			ci.cancel();
		}
	}
}
