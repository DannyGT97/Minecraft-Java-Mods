package com.dannygt97.inventoryfilters.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class IconButton extends Button {
    private final Identifier icon;
    private final Identifier selectedIcon;
    private boolean selected;

    public IconButton(int x, int y, int size, Component message, Identifier icon, Identifier selectedIcon, OnPress onPress) {
        super(x, y, size, size, message, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.selectedIcon = selectedIcon;
    }

    public void setSelected(boolean selected) { this.selected = selected; }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Identifier texture = selected || isHovered() ? selectedIcon : icon;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, getX(), getY(), 0.0F, 0.0F, getWidth(), getHeight(), getWidth(), getHeight());
    }
}
