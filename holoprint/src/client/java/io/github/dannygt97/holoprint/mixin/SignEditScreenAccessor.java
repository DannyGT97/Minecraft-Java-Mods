package io.github.dannygt97.holoprint.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

/** Lets the easy placement write the text a hologram's sign has into the sign editor that opens when the sign is placed. */
@Mixin(AbstractSignEditScreen.class)
public interface SignEditScreenAccessor {
	@Accessor("messages")
	String[] holoprint$messages();

	@Accessor("sign")
	SignBlockEntity holoprint$sign();

	@Accessor("slot")
	SignTextSlot holoprint$slot();
}
