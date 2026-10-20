package io.github.dannygt97.holoprint.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.Minecraft;

/** Lets the easy placement run the game's own use-item click a few ticks later than the click that asked for it. */
@Mixin(Minecraft.class)
public interface UseItemInvoker {
	@Invoker("startUseItem")
	void holoprint$startUseItem();
}
