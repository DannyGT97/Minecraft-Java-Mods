package com.dannygt97.inventoryfilters.inventory;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonParser;
import com.dannygt97.inventoryfilters.network.LoadHotbarPresetPayload;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/** Resolves a saved layout against the player's current inventory and applies it without dropping items. */
public final class HotbarPresetLoader {
	private HotbarPresetLoader() {}

	public static void load(ServerPlayer player, LoadHotbarPresetPayload payload) {
		if (!(player.containerMenu instanceof InventoryMenu)) return;
		Inventory inventory = player.getInventory();
		RegistryAccess registries = player.registryAccess();
		RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
		List<ItemStack> wanted = new ArrayList<>(9);
		for (String encoded : payload.slots()) {
			if (encoded.isEmpty()) {
				wanted.add(ItemStack.EMPTY);
				continue;
			}
			try {
				wanted.add(ItemStack.CODEC.parse(ops, JsonParser.parseString(encoded))
					.result().orElse(ItemStack.EMPTY));
			} catch (RuntimeException exception) {
				// A stack from an unavailable mod or registry is treated as absent in this slot.
				wanted.add(ItemStack.EMPTY);
			}
		}

		List<ItemStack> simulated = new ArrayList<>(Inventory.INVENTORY_SIZE);
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) simulated.add(inventory.getItem(i).copy());
		List<ItemStack> loaded = new ArrayList<>(9);
		for (ItemStack target : wanted) {
			if (target.isEmpty()) {
				loaded.add(ItemStack.EMPTY);
				continue;
			}
			int remaining = target.getCount();
			ItemStack result = ItemStack.EMPTY;
			// Preserve matching items already in their destination slot, including their actual
			// enchantments when matching is configured to ignore enchantments.
			ItemStack alreadyInSlot = simulated.get(loaded.size());
			if (matches(alreadyInSlot, target, payload.ignoreEnchantments())) {
				int retained = Math.min(remaining, alreadyInSlot.getCount());
				result = alreadyInSlot.copy();
				result.setCount(retained);
				remaining -= retained;
				ItemStack excess = alreadyInSlot.copy();
				excess.shrink(retained);
				simulated.set(loaded.size(), excess.isEmpty() ? ItemStack.EMPTY : excess);
			}
			for (int source = 0; source < simulated.size() && remaining > 0; source++) {
				ItemStack available = simulated.get(source);
				if (available.isEmpty() || !matches(available, target, payload.ignoreEnchantments())) continue;
				// Keep component-bearing stacks intact: in relaxed mode, items with different
				// enchantments still cannot be merged into one stack without changing their data.
				if (!result.isEmpty() && !ItemStack.isSameItemSameComponents(available, result)) continue;
				if (result.isEmpty()) {
					result = available.copy();
					result.setCount(0);
				}
				int moved = Math.min(remaining, available.getCount());
				result.grow(moved);
				available.shrink(moved);
				if (available.isEmpty()) simulated.set(source, ItemStack.EMPTY);
				remaining -= moved;
			}
			loaded.add(result.isEmpty() || result.getCount() == 0 ? ItemStack.EMPTY : result);
		}

		List<ItemStack> displaced = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) {
			ItemStack remaining = simulated.get(i);
			if (!remaining.isEmpty()) displaced.add(remaining.copy());
			simulated.set(i, ItemStack.EMPTY);
		}
		for (ItemStack stack : displaced) {
			if (!insertIntoMainInventory(simulated, stack)) {
				player.sendOverlayMessage(Component.translatable("inventoryfilters.preset.inventory_full"));
				return;
			}
		}

		for (int i = 0; i < 9; i++) inventory.setItem(i, loaded.get(i));
		for (int i = 9; i < Inventory.INVENTORY_SIZE; i++) inventory.setItem(i, simulated.get(i));
		player.containerMenu.broadcastChanges();
	}

	private static boolean matches(ItemStack available, ItemStack wanted, boolean ignoreEnchantments) {
		if (!ignoreEnchantments) return HotbarPresetMatch.matchesIncludingEnchantments(available, wanted);
		return HotbarPresetMatch.matchesIgnoringEnchantments(available, wanted);
	}

	private static boolean insertIntoMainInventory(List<ItemStack> slots, ItemStack incoming) {
		ItemStack remaining = incoming.copy();
		for (int i = 9; i < Inventory.INVENTORY_SIZE && !remaining.isEmpty(); i++) {
			ItemStack existing = slots.get(i);
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)
					&& existing.getCount() < existing.getMaxStackSize()) {
					int moved = Math.min(existing.getMaxStackSize() - existing.getCount(), remaining.getCount());
					existing.grow(moved);
					remaining.shrink(moved);
			}
		}
		for (int i = 9; i < Inventory.INVENTORY_SIZE && !remaining.isEmpty(); i++) {
			if (slots.get(i).isEmpty()) {
				int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
				ItemStack placed = remaining.copy();
				placed.setCount(moved);
				slots.set(i, placed);
				remaining.shrink(moved);
			}
		}
		return remaining.isEmpty();
	}
}
