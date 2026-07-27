package com.rtsbuilding.rtsbuilding.api.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public interface RtsBackpackProvider {

    String getModId();

    boolean isBackpackBlockEntity(BlockEntity be);

    Optional<UUID> getBackpackUuid(BlockEntity be);

    Optional<String> getBackpackItemId(BlockEntity be);

    Optional<IItemHandler> openBackpack(UUID uuid, String itemId, ServerPlayer player);
}
