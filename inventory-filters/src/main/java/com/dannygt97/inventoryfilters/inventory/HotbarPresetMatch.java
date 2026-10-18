package com.dannygt97.inventoryfilters.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Shared preset matching rules for server-side loading and client-side previews. */
public final class HotbarPresetMatch {
	private HotbarPresetMatch() {}

	public static boolean matchesIgnoringEnchantments(ItemStack first, ItemStack second) {
		return !first.isEmpty() && !second.isEmpty() && first.getItem() == second.getItem();
	}

	public static boolean matchesIncludingEnchantments(ItemStack first, ItemStack second) {
		if (first.isEmpty() || second.isEmpty() || first.getItem() != second.getItem()) return false;
		// A preset's exact mode means the same item with the same enchantments.
		// Durability and other changing item data must not prevent a tool from matching.
		return Objects.equals(first.get(DataComponents.ENCHANTMENTS), second.get(DataComponents.ENCHANTMENTS))
			&& Objects.equals(first.get(DataComponents.STORED_ENCHANTMENTS), second.get(DataComponents.STORED_ENCHANTMENTS));
	}

	public static ItemStack withoutEnchantments(ItemStack stack) {
		if (stack.isEmpty()) return ItemStack.EMPTY;
		ItemStack copy = stack.copy();
		copy.remove(DataComponents.ENCHANTMENTS);
		copy.remove(DataComponents.STORED_ENCHANTMENTS);
		return copy;
	}
}
