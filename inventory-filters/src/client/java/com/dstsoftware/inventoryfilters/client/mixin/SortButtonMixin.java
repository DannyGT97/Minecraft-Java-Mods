package com.dstsoftware.inventoryfilters.client.mixin;

import com.dstsoftware.inventoryfilters.client.config.InventoryFiltersConfig;
import com.dstsoftware.inventoryfilters.client.gui.ButtonTheme;
import com.dstsoftware.inventoryfilters.client.gui.IconButton;
import com.dstsoftware.inventoryfilters.client.gui.SortButtonHelper;
import com.dstsoftware.inventoryfilters.inventory.InventorySorter;
import com.dstsoftware.inventoryfilters.network.ContainerTransferPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class SortButtonMixin extends Screen {
    private static final int SIZE = 14;
    private static final int PITCH = 14;
    private static final int GAP = 2;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;
    @Shadow public abstract AbstractContainerMenu getMenu();

    @Unique private final Button[] inventoryfilters$containerButtons = new Button[3];
    @Unique private final Button[] inventoryfilters$playerButtons = new Button[3];

    protected SortButtonMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void inventoryfilters$addButtons(CallbackInfo ci) {
        AbstractContainerMenu menu = getMenu();
        if (!InventoryFiltersConfig.isEnabled() || !InventorySorter.supports(menu)) return;
        ButtonTheme theme = InventoryFiltersConfig.getButtonTheme();

        if (InventorySorter.hasContainerSection(menu)) {
            inventoryfilters$containerButtons[0] = inventoryfilters$addButton(
                theme.icon("sort"), theme.selectedIcon("sort"), SortButtonHelper.tooltip(),
                b -> inventoryfilters$sortClicked(false)
            );
            inventoryfilters$containerButtons[1] = inventoryfilters$addButton(
                theme.icon("transfer"), theme.selectedIcon("transfer"),
                Component.translatable("inventoryfilters.stack_from_container.tooltip"),
                b -> inventoryfilters$actionClicked(false, true, inventoryfilters$containerButtons, 1)
            );
            inventoryfilters$containerButtons[2] = inventoryfilters$addButton(
                theme.icon("stack_down"), theme.selectedIcon("stack_down"),
                Component.translatable("inventoryfilters.take_all.tooltip"),
                b -> inventoryfilters$actionClicked(false, false, inventoryfilters$containerButtons, 2)
            );
        }

        inventoryfilters$playerButtons[0] = inventoryfilters$addButton(
            theme.icon("sort"), theme.selectedIcon("sort"), SortButtonHelper.tooltip(),
            b -> inventoryfilters$sortClicked(true)
        );
        if (InventorySorter.hasContainerSection(menu)) {
            inventoryfilters$playerButtons[1] = inventoryfilters$addButton(
                theme.icon("transfer"), theme.selectedIcon("transfer"),
                Component.translatable("inventoryfilters.stack_into_container.tooltip"),
                b -> inventoryfilters$actionClicked(true, true, inventoryfilters$playerButtons, 1)
            );
            inventoryfilters$playerButtons[2] = inventoryfilters$addButton(
                theme.icon("stack_up"), theme.selectedIcon("stack_up"),
                Component.translatable("inventoryfilters.place_all.tooltip"),
                b -> inventoryfilters$actionClicked(true, false, inventoryfilters$playerButtons, 2)
            );
        }
        inventoryfilters$layoutButtons();
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void inventoryfilters$repositionButtons(CallbackInfo ci) {
        inventoryfilters$layoutButtons();
    }

    @Unique
    private Button inventoryfilters$addButton(Identifier icon, Identifier selectedIcon, Component tooltip, Button.OnPress onPress) {
        IconButton button = new IconButton(0, 0, SIZE, tooltip, icon, selectedIcon, onPress);
        button.setTooltip(Tooltip.create(tooltip));
        addRenderableWidget(button);
        return button;
    }

    @Unique
    private void inventoryfilters$layoutButtons() {
        int x = leftPos + imageWidth + GAP;
        int playerTop = topPos + imageHeight - 82;
        int containerTop = topPos + 18;
        AbstractContainerMenu menu = getMenu();
        Container external = InventorySorter.externalContainer(menu);
        if (external != null && !menu.slots.isEmpty()) {
            containerTop = topPos + menu.getSlot(0).y;
            int firstPlayerSlot = external.getContainerSize();
            if (firstPlayerSlot < menu.slots.size()) {
                playerTop = topPos + menu.getSlot(firstPlayerSlot).y;
            }
            int maxContainerTop = playerTop - 3 * PITCH - GAP;
            if (containerTop > maxContainerTop) containerTop = maxContainerTop;
        }
        for (int i = 0; i < 3; i++) {
            if (inventoryfilters$containerButtons[i] != null) {
                inventoryfilters$containerButtons[i].setPosition(x, containerTop + PITCH * i);
            }
            if (inventoryfilters$playerButtons[i] != null) {
                inventoryfilters$playerButtons[i].setPosition(x, playerTop + PITCH * i);
            }
        }
    }

    @Unique
    private void inventoryfilters$sortClicked(boolean playerInventory) {
        SortButtonHelper.send(playerInventory);
        Tooltip tooltip = Tooltip.create(SortButtonHelper.tooltip());
        if (inventoryfilters$containerButtons[0] != null) inventoryfilters$containerButtons[0].setTooltip(tooltip);
        if (inventoryfilters$playerButtons[0] != null) inventoryfilters$playerButtons[0].setTooltip(tooltip);
        inventoryfilters$markSelected((playerInventory ? inventoryfilters$playerButtons : inventoryfilters$containerButtons)[0]);
    }

    @Unique
    private void inventoryfilters$actionClicked(boolean toContainer, boolean stackOnly, Button[] column, int index) {
        if (!InventoryFiltersConfig.isEnabled()) return;
        if (!ClientPlayNetworking.canSend(ContainerTransferPayload.TYPE)) {
            SortButtonHelper.showServerRequired();
            return;
        }
        ClientPlayNetworking.send(new ContainerTransferPayload(toContainer, stackOnly, InventoryFiltersConfig.isIncludeHotbar()));
        inventoryfilters$markSelected(column[index]);
    }

    @Unique
    private void inventoryfilters$markSelected(Button selected) {
        for (Button[] column : new Button[][] {inventoryfilters$containerButtons, inventoryfilters$playerButtons}) {
            for (Button button : column) {
                if (button instanceof IconButton iconButton) iconButton.setSelected(button == selected);
            }
        }
    }
}

