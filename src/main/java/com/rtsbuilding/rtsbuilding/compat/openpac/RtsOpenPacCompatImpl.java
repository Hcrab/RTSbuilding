package com.rtsbuilding.rtsbuilding.compat.openpac;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.UUID;

final class RtsOpenPacCompatImpl {
    private final Method apiGetMethod;
    private final Method getPartyManagerMethod;
    private final Method getPartyByMemberMethod;
    private final Method getPartyIdMethod;
    private final Method getPartyDefaultNameMethod;
    private final Method getChunkProtectionMethod;
    private final Method onBlockInteractionMethod;
    private final Method onEntityPlaceBlockMethod;
    private final Method onEntityInteractionMethod;
    private boolean warnedRuntimeFailure;

    RtsOpenPacCompatImpl() throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
        this.apiGetMethod = apiClass.getMethod("get", MinecraftServer.class);
        this.getPartyManagerMethod = apiClass.getMethod("getPartyManager");
        this.getChunkProtectionMethod = apiClass.getMethod("getChunkProtection");

        Class<?> partyManagerClass = this.getPartyManagerMethod.getReturnType();
        this.getPartyByMemberMethod = partyManagerClass.getMethod("getPartyByMember", UUID.class);
        Class<?> partyClass = this.getPartyByMemberMethod.getReturnType();
        this.getPartyIdMethod = partyClass.getMethod("getId");
        this.getPartyDefaultNameMethod = partyClass.getMethod("getDefaultName");

        Class<?> protectionClass = this.getChunkProtectionMethod.getReturnType();
        this.onBlockInteractionMethod = protectionClass.getMethod(
                "onBlockInteraction",
                Entity.class,
                InteractionHand.class,
                ItemStack.class,
                ServerLevel.class,
                BlockPos.class,
                Direction.class,
                boolean.class,
                boolean.class,
                boolean.class);
        this.onEntityPlaceBlockMethod = protectionClass.getMethod(
                "onEntityPlaceBlock",
                Entity.class,
                ServerLevel.class,
                BlockPos.class);
        this.onEntityInteractionMethod = protectionClass.getMethod(
                "onEntityInteraction",
                Entity.class,
                Entity.class,
                Entity.class,
                ItemStack.class,
                InteractionHand.class,
                boolean.class,
                boolean.class,
                boolean.class);
    }

    TeamInfo teamInfo(ServerPlayer player) {
        try {
            Object manager = this.getPartyManagerMethod.invoke(api(player));
            if (manager == null) {
                return null;
            }
            Object party = this.getPartyByMemberMethod.invoke(manager, player.getUUID());
            if (party == null) {
                return null;
            }
            Object id = this.getPartyIdMethod.invoke(party);
            if (!(id instanceof UUID uuid)) {
                return null;
            }
            String label = "";
            Object defaultName = this.getPartyDefaultNameMethod.invoke(party);
            if (defaultName != null) {
                label = defaultName.toString().trim();
            }
            if (label.isBlank()) {
                label = "OpenPAC";
            }
            return new TeamInfo("openpac:" + uuid, label);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("OpenPAC party lookup failed; falling back to non-party RTS progression.", exception);
            return null;
        }
    }

    boolean canBreakBlock(ServerPlayer player, BlockPos pos, Direction face) {
        return !isBlockInteractionProtected(player, pos, face, InteractionHand.MAIN_HAND,
                player.getMainHandItem(), true);
    }

    boolean canPlaceBlock(ServerPlayer player, BlockPos pos) {
        try {
            Object protection = this.getChunkProtectionMethod.invoke(api(player));
            if (protection == null) {
                return true;
            }
            Object protectedResult = this.onEntityPlaceBlockMethod.invoke(
                    protection,
                    player,
                    player.level(),
                    pos);
            return !Boolean.TRUE.equals(protectedResult);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("OpenPAC block placement check failed; allowing RTS placement for this action.", exception);
            return true;
        }
    }

    boolean canInteractBlock(ServerPlayer player, BlockPos pos, Direction face, InteractionHand hand, ItemStack heldItem) {
        return !isBlockInteractionProtected(player, pos, face, hand, heldItem, false);
    }

    boolean canInteractEntity(ServerPlayer player, Entity target, InteractionHand hand, ItemStack heldItem, boolean attack) {
        try {
            Object protection = this.getChunkProtectionMethod.invoke(api(player));
            if (protection == null) {
                return true;
            }
            Object protectedResult = this.onEntityInteractionMethod.invoke(
                    protection,
                    null,
                    player,
                    target,
                    heldItem,
                    hand,
                    attack,
                    true,
                    true);
            return !Boolean.TRUE.equals(protectedResult);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("OpenPAC entity interaction check failed; allowing RTS interaction for this action.", exception);
            return true;
        }
    }

    private boolean isBlockInteractionProtected(ServerPlayer player, BlockPos pos, Direction face,
            InteractionHand hand, ItemStack heldItem, boolean breaking) {
        try {
            Object protection = this.getChunkProtectionMethod.invoke(api(player));
            if (protection == null) {
                return false;
            }
            Object protectedResult = this.onBlockInteractionMethod.invoke(
                    protection,
                    player,
                    hand,
                    heldItem,
                    player.level(),
                    pos,
                    face,
                    breaking,
                    true,
                    true);
            return Boolean.TRUE.equals(protectedResult);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("OpenPAC block interaction check failed; allowing RTS action for this action.", exception);
            return false;
        }
    }

    private Object api(ServerPlayer player) throws ReflectiveOperationException {
        return this.apiGetMethod.invoke(null, player.level().getServer());
    }

    private void warnOnce(String message, Throwable throwable) {
        if (this.warnedRuntimeFailure) {
            return;
        }
        this.warnedRuntimeFailure = true;
        RtsbuildingMod.LOGGER.warn(message, throwable);
    }

    record TeamInfo(String key, String label) {
    }
}
