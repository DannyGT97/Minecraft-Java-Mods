package com.dannygt97.inventoryfilters.mixin;

import com.dannygt97.inventoryfilters.inventory.BlockAutoRefillManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@Unique private ItemStack inventoryfilters$usedBlockStack = ItemStack.EMPTY;

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void inventoryfilters$captureBlockStack(ServerPlayer player, Level level, ItemStack stack,
			InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		inventoryfilters$usedBlockStack = stack.getItem() instanceof BlockItem ? stack.copy() : ItemStack.EMPTY;
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void inventoryfilters$refillDepletedBlock(ServerPlayer player, Level level, ItemStack stack,
			InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack used = inventoryfilters$usedBlockStack;
		inventoryfilters$usedBlockStack = ItemStack.EMPTY;
		if (!used.isEmpty() && cir.getReturnValue().consumesAction() && player.getItemInHand(hand).isEmpty()) {
			BlockAutoRefillManager.refillIfEmpty(player, hand, used);
		}
	}
}
