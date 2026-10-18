package com.dannygt97.inventoryfilters.network;

import java.util.ArrayList;
import java.util.List;

import com.dannygt97.inventoryfilters.InventoryFilters;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Carries one saved hotbar layout to the server for an authoritative inventory update. */
public record LoadHotbarPresetPayload(List<String> slots, boolean ignoreEnchantments) implements CustomPacketPayload {
	public static final Type<LoadHotbarPresetPayload> TYPE = new Type<>(InventoryFilters.id("load_hotbar_preset"));
	public static final StreamCodec<FriendlyByteBuf, LoadHotbarPresetPayload> CODEC = StreamCodec.of(
		(buf, payload) -> {
			for (int i = 0; i < 9; i++) buf.writeUtf(payload.slots().get(i), 32767);
			buf.writeBoolean(payload.ignoreEnchantments());
		},
		buf -> {
			List<String> slots = new ArrayList<>(9);
			for (int i = 0; i < 9; i++) slots.add(buf.readUtf(32767));
			return new LoadHotbarPresetPayload(List.copyOf(slots), buf.readBoolean());
		}
	);

	public LoadHotbarPresetPayload {
		if (slots.size() != 9) throw new IllegalArgumentException("A hotbar preset must contain nine slots");
		slots = List.copyOf(slots);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
