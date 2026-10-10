package com.dstsoftware.inventoryfilters.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

// Sorts whichever container is open: the player's own main storage (never the hotbar - the 9
// slots stay put on purpose) when in the plain inventory screen, or the chest-shaped container
// itself  otherwise.
// Screens with a distinct external container (see hasContainerSection) get a second button for
// sorting the player's own inventory without leaving the screen - sortPlayerInventory picks
// between the two on those.
public class InventorySorter {
	public static final int MODE_TYPE = 0;
	public static final int MODE_NAME = 1;
	public static final int MODE_CATEGORY = 2;
	public static final int MODE_RARITY = 3;
	public static final int MODE_COUNT = 4;

	public static boolean supports(final AbstractContainerMenu menu) {
		// Any menu with player-inventory slots (crafting table, anvil, grindstone, smithing
		// table, stonecutter, loom, cartography table, enchanting table, brewing stand,
		// furnace/blast furnace/smoker, beacon, villager trading, the Miner Helmet refuel
		// screen, ...) gets at least the player-inventory sort button - detected generically by
		// slot backing container rather than an explicit per-menu-class allowlist, so any future
		// vanilla or modded screen with a player inventory section picks this up automatically.
		// Their own special-purpose slots (crafting grid, anvil input, furnace fuel/output, ...)
		// aren't meaningfully sortable, so hasContainerSection deliberately doesn't cover them -
		// same reasoning already applied to the Miner Helmet's 5 fuel slots.
		return menu instanceof InventoryMenu || hasContainerSection(menu) || hasPlayerInventorySlots(menu);
	}

	private static boolean hasPlayerInventorySlots(final AbstractContainerMenu menu) {
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory) {
				return true;
			}
		}
		return false;
	}

	// Menus with a distinct external container, separate from the player's own inventory grid
	// it isn't a ChestMenu subclass - its "external container" is just the current page's window
	public static boolean hasContainerSection(final AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
	}

	public static void sort(final ServerPlayer player, final int mode, final boolean sortPlayerInventory) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!supports(menu)) {
			return;
		}

		if (sortPlayerInventory || menu instanceof InventoryMenu) {
			sortRange(player.getInventory(), Inventory.SELECTION_SIZE, Inventory.INVENTORY_SIZE, mode);
		} else {
			Container container = externalContainer(menu);
			if (container != null) {
				sortRange(container, 0, container.getContainerSize(), mode);
			}
		}

		menu.broadcastChanges();
	}

	// Resolves the external chest or shulker container opened by the player.
	// chest/shulker-box-shaped menu is backed by, or null if the menu has no such section.
	public static Container externalContainer(final AbstractContainerMenu menu) {
		if (menu instanceof ChestMenu chestMenu) {
			return chestMenu.getContainer();
		} else if (menu instanceof ShulkerBoxMenu) {
			// Neither exposes its backing container directly - every menu's own slot 0 does,
			// through the public Slot.container field, since slot 0 is always the external
			return menu.getSlot(0).container;
		}
		return null;
	}

	private static void sortRange(final Container container, final int from, final int to, final int mode) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = from; i < to; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				stacks.add(stack.copy());
			}
		}

		List<ItemStack> merged = mergeStacks(stacks);
		merged.sort(comparatorFor(mode));

		int index = from;
		for (ItemStack stack : merged) {
			container.setItem(index++, stack);
		}
		while (index < to) {
			container.setItem(index++, ItemStack.EMPTY);
		}
	}

	private static List<ItemStack> mergeStacks(final List<ItemStack> stacks) {
		List<ItemStack> merged = new ArrayList<>();
		for (ItemStack stack : stacks) {
			boolean absorbed = false;
			for (ItemStack existing : merged) {
				if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
					int move = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
					existing.grow(move);
					stack.shrink(move);
					if (stack.isEmpty()) {
						absorbed = true;
						break;
					}
				}
			}
			if (!stack.isEmpty() && !absorbed) {
				merged.add(stack);
			}
		}
		return merged;
	}

	private static Comparator<ItemStack> comparatorFor(final int mode) {
		return switch (mode) {
			case MODE_NAME -> Comparator.comparing(stack -> stack.getHoverName().getString());
			case MODE_CATEGORY -> Comparator.<ItemStack, Integer>comparing(InventorySorter::categoryBucket).thenComparing(stack -> stack.getHoverName().getString());
			case MODE_RARITY -> Comparator.<ItemStack, Integer>comparing(stack -> -stack.getRarity().ordinal()).thenComparing(stack -> stack.getHoverName().getString());
			default -> Comparator.<ItemStack, Integer>comparing(InventorySorter::typeBucket).thenComparing(stack -> stack.getHoverName().getString());
		};
	}

	// Rough approximation until real category data exists - block/armor/tool/food/misc.
	// Armor and tools no longer have dedicated item classes in this version; they're plain
	// Items carrying an EQUIPPABLE or TOOL/WEAPON data component instead.
	private static int typeBucket(final ItemStack stack) {
		if (stack.getItem() instanceof BlockItem) {
			return 0;
		} else if (stack.has(DataComponents.EQUIPPABLE)) {
			return 1;
		} else if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)) {
			return 2;
		} else if (stack.has(DataComponents.FOOD)) {
			return 3;
		} else {
			return 4;
		}
	}

	// Deliberately different grouping from typeBucket (combat/tools, building, food, misc)
	// so "Categoría" reads as a distinct mode from "Tipo" even with simple heuristics.
	private static int categoryBucket(final ItemStack stack) {
		if (stack.has(DataComponents.EQUIPPABLE) || stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)) {
			return 0;
		} else if (stack.getItem() instanceof BlockItem) {
			return 1;
		} else if (stack.has(DataComponents.FOOD)) {
			return 2;
		} else {
			return 3;
		}
	}
}



