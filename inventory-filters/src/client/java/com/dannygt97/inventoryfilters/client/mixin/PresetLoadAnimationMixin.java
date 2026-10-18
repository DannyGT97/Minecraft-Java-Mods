package com.dannygt97.inventoryfilters.client.mixin;

import com.dannygt97.inventoryfilters.client.gui.PresetLoadAnimationSuppressor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class PresetLoadAnimationMixin {
	@Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
	private void inventoryfilters$removePresetHotbarPop(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		if (packet.getContainerId() != 0 || !InventoryMenu.isHotbarSlot(packet.getSlot())) return;
		int inventorySlot = packet.getSlot() - 36;
		if (!PresetLoadAnimationSuppressor.shouldSuppressHotbarSlot(inventorySlot)) return;

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return;
		ItemStack updatedPacketStack = packet.getItem();
		if (!updatedPacketStack.isEmpty()) updatedPacketStack.setPopTime(0);
		ItemStack updatedInventoryStack = minecraft.player.getInventory().getItem(inventorySlot);
		if (!updatedInventoryStack.isEmpty()) updatedInventoryStack.setPopTime(0);
	}
}
