package com.dstsoftware.inventoryfilters.network;

import com.dstsoftware.inventoryfilters.InventoryFilters;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ContainerTransferPayload(boolean toContainer, boolean stackOnly) implements CustomPacketPayload {
    public static final Type<ContainerTransferPayload> TYPE =
        new Type<>(InventoryFilters.id("container_transfer"));
    public static final StreamCodec<FriendlyByteBuf, ContainerTransferPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, ContainerTransferPayload::toContainer,
        ByteBufCodecs.BOOL, ContainerTransferPayload::stackOnly,
        ContainerTransferPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
