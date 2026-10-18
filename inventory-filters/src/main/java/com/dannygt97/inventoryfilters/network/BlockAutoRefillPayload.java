package com.dannygt97.inventoryfilters.network;

import com.dannygt97.inventoryfilters.InventoryFilters;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BlockAutoRefillPayload(boolean enabled) implements CustomPacketPayload {
	public static final Type<BlockAutoRefillPayload> TYPE = new Type<>(InventoryFilters.id("block_auto_refill"));
	public static final StreamCodec<FriendlyByteBuf, BlockAutoRefillPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, BlockAutoRefillPayload::enabled, BlockAutoRefillPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
