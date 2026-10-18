package com.dannygt97.inventoryfilters.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative refill of depleted block stacks in either hand. */
public final class BlockAutoRefillManager {
	private static final Map<UUID, Boolean> ENABLED_PLAYERS = new HashMap<>();

	private BlockAutoRefillManager() {}

	public static void setEnabled(ServerPlayer player, boolean enabled) {
		if (enabled) ENABLED_PLAYERS.put(player.getUUID(), true);
		else ENABLED_PLAYERS.remove(player.getUUID());
	}

	public static void forget(ServerPlayer player) {
		ENABLED_PLAYERS.remove(player.getUUID());
	}

	public static void refillIfEmpty(ServerPlayer player, InteractionHand hand, ItemStack depletedStack) {
		if (!ENABLED_PLAYERS.containsKey(player.getUUID()) || depletedStack.isEmpty()
				|| !(depletedStack.getItem() instanceof BlockItem) || !player.getItemInHand(hand).isEmpty()) return;

		Inventory inventory = player.getInventory();
		ItemStack replacement = ItemStack.EMPTY;
		int replacementMaxStackSize = 0;
		for (int slot = 9; slot < 36; slot++) {
			ItemStack available = inventory.getItem(slot);
			if (!ItemStack.isSameItemSameComponents(available, depletedStack)) continue;
			if (replacement.isEmpty()) {
				replacementMaxStackSize = available.getMaxStackSize();
				replacement = available.copy();
				replacement.setCount(0);
			}
			int moved = Math.min(available.getCount(), replacementMaxStackSize - replacement.getCount());
			if (moved <= 0) break;
			replacement.grow(moved);
			available.shrink(moved);
			if (available.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
			if (replacement.getCount() >= replacementMaxStackSize) break;
		}
		if (!replacement.isEmpty()) {
			int handSlot = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : Inventory.SLOT_OFFHAND;
			inventory.setItem(handSlot, replacement);
			player.connection.send(new ClientboundSetPlayerInventoryPacket(handSlot, replacement.copy()));
			player.inventoryMenu.broadcastChanges();
			if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
		}
	}
}
