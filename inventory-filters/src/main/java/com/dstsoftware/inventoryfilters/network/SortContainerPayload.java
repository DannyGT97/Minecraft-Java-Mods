package com.dstsoftware.inventoryfilters.network;

import com.dstsoftware.inventoryfilters.InventoryFilters;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SortContainerPayload(int mode, boolean playerInventory) implements CustomPacketPayload {
    public static final Type<SortContainerPayload> TYPE = new Type<>(InventoryFilters.id("sort_container"));
    public static final StreamCodec<FriendlyByteBuf, SortContainerPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SortContainerPayload::mode,
        ByteBufCodecs.BOOL, SortContainerPayload::playerInventory,
        SortContainerPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
