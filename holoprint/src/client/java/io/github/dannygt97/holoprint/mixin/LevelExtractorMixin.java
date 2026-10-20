package io.github.dannygt97.holoprint.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.dannygt97.holoprint.freecam.Freecam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;

// The game hides the local player whenever the camera is on some other entity (that is how it
// treats spectating): the last time the method asks which entity the camera is on, the answer
// decides whether the local player is skipped. While the free camera flies the answer given there
// is the player itself, so the player is drawn standing where they are, in third person. The other
// three questions (is this the first-person view?) keep their real answer.
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@ModifyExpressionValue(method = "extractVisibleEntities",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;entity()Lnet/minecraft/world/entity/Entity;", ordinal = 3))
	private Entity holoprint$showPlayerInFreecam(final Entity cameraEntity) {
		return Freecam.isActive() ? Minecraft.getInstance().player : cameraEntity;
	}
}
