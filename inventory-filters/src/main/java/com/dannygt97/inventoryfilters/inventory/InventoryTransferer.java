package com.dannygt97.inventoryfilters.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

// Backs the Transfer-all and Auto-stack buttons: bulk-moves items between the player's own main
// storage (hotbar only when asked for, same as InventorySorter) and whatever external container is open.
// Transfer-all moves everything; auto-stack only moves the items the destination already holds
// (quick-stack), topping up its partial stacks first and then using free slots. Both walk the
// source front-to-back and greedily fill the destination - the same
// mechanics as repeatedly shift-clicking every source slot.
public class InventoryTransferer {
	public static void transferAll(final ServerPlayer player, final boolean toContainer, final boolean includeHotbar) {
		run(player, toContainer, false, includeHotbar);
	}

	public static void autoStack(final ServerPlayer player, final boolean toContainer, final boolean includeHotbar) {
		run(player, toContainer, true, includeHotbar);
	}

	private static void run(final ServerPlayer player, final boolean toContainer, final boolean stackOnly, final boolean includeHotbar) {
		AbstractContainerMenu menu = player.containerMenu;
		Container container = InventorySorter.externalContainer(menu);
		if (container == null) {
			return;
		}

		Inventory inventory = player.getInventory();
		int firstSlot = InventorySorter.firstPlayerSlot(includeHotbar);
		if (toContainer) {
			move(inventory, firstSlot, Inventory.INVENTORY_SIZE, container, 0, container.getContainerSize(), stackOnly, menu, true);
		} else {
			move(container, 0, container.getContainerSize(), inventory, firstSlot, Inventory.INVENTORY_SIZE, stackOnly, menu, false);
		}

		menu.broadcastChanges();
	}

	// stackOnly restricts the move to items the destination already holds (any stack of the same
	// item, whatever its count) - the difference between "auto-stack" and a full "transfer all".
	// Those items first top up the matching partial stacks, then spill into free slots, so a
	// container whose existing stacks are already full still takes the rest of that item.
	// destIsContainer gates the "place into an empty slot" step through the menu's real
	// Slot.mayPlace (see mayPlace below) - without this, this bulk path wrote straight to the raw
	// Container and completely bypassed container slot placement restrictions, letting a single
	// player dump past them in one click via the "place all" button.
	private static void move(
		final Container source,
		final int srcFrom,
		final int srcTo,
		final Container dest,
		final int destFrom,
		final int destTo,
		final boolean stackOnly,
		final AbstractContainerMenu menu,
		final boolean destIsContainer
	) {
		for (int i = srcFrom; i < srcTo; i++) {
			ItemStack stack = source.getItem(i);
			if (stack.isEmpty() || (stackOnly && !holdsItem(dest, destFrom, destTo, stack))) {
				continue;
			}

			for (int j = destFrom; j < destTo && !stack.isEmpty(); j++) {
				ItemStack destStack = dest.getItem(j);
				if (!destStack.isEmpty() && ItemStack.isSameItemSameComponents(destStack, stack) && destStack.getCount() < destStack.getMaxStackSize()) {
					int move = Math.min(destStack.getMaxStackSize() - destStack.getCount(), stack.getCount());
					destStack.grow(move);
					stack.shrink(move);
				}
			}

			for (int j = destFrom; j < destTo && !stack.isEmpty(); j++) {
				if (dest.getItem(j).isEmpty() && mayPlace(menu, destIsContainer, j, stack)) {
					int move = Math.min(stack.getMaxStackSize(), stack.getCount());
					ItemStack placed = stack.copy();
					placed.setCount(move);
					dest.setItem(j, placed);
					stack.shrink(move);
				}
			}

			source.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
		}
	}

	private static boolean holdsItem(final Container container, final int from, final int to, final ItemStack stack) {
		for (int j = from; j < to; j++) {
			if (ItemStack.isSameItem(container.getItem(j), stack)) {
				return true;
			}
		}
		return false;
	}

	// Only the container side of a transfer needs gating - the player's own inventory has no
	// placement restrictions. Reuses the exact same Slot.mayPlace a manual click would go through:
	// container-local index j lines up 1:1 with menu.getSlot(j) for every menu
	// InventorySorter.hasContainerSection() supports (see InventorySorter.externalContainer, which
	// already relies on this same alignment) - so any current or future per-container placement
	// rule is enforced here too, not just on manual single-slot clicks.
	private static boolean mayPlace(final AbstractContainerMenu menu, final boolean destIsContainer, final int localSlot, final ItemStack stack) {
		return !destIsContainer || menu.getSlot(localSlot).mayPlace(stack);
	}
}

