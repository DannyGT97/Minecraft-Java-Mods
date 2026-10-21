package io.github.dannygt97.holoprint.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Lets the hologram entities reach the layers of an item that is about to be drawn, to make them translucent. */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] holoprint$layers();

	@Accessor("activeLayerCount")
	int holoprint$layerCount();
}
