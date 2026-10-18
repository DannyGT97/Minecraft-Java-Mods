package com.dannygt97.gameplaytweaks.client.mixin;

import com.dannygt97.gameplaytweaks.client.ReachAroundPlacement;
import com.dannygt97.gameplaytweaks.client.config.GameplayTweaksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow public LocalPlayer player;
	@Shadow public MultiPlayerGameMode gameMode;
	@Shadow public HitResult hitResult;
	@Shadow private int rightClickDelay;

	@Inject(method = "tick", at = @At("TAIL"))
	private void gameplaytweaks$updateBridgeTarget(CallbackInfo ci) {
		ReachAroundPlacement.tick((Minecraft) (Object) this);
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void gameplaytweaks$placeWithBridging(CallbackInfo ci) {
		if (!GameplayTweaksConfig.isBridgingEnabled() || player == null || gameMode == null
				|| hitResult == null || hitResult.getType() != HitResult.Type.MISS
				|| player.isHandsBusy() || gameMode.isDestroying()) return;

		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(stack.getItem() instanceof BlockItem)) continue;
			BlockHitResult target = ReachAroundPlacement.getLastTickTarget();
			if (target == null) continue;

			InteractionResult result = gameMode.useItemOn(player, hand, target);
			if (!result.consumesAction()) continue;

			rightClickDelay = 4;
			ci.cancel();
			if (result instanceof InteractionResult.Success success
					&& success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
				player.swing(hand, stack.getInteractAnimation(), false);
			}
			return;
		}
	}
}
