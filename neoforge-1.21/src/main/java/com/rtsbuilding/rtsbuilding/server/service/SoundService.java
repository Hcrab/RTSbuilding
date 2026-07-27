package com.rtsbuilding.rtsbuilding.server.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/**
 * Remote interaction sound service — encapsulates sound playback logic for remote operations in RTS mode.
 *
 * <p>This service handles sound feedback after interacting with blocks/entities in remote camera mode,
 * including sound selection, network packet sending, and ItemStack construction.
 * All methods are {@code static}, the class itself is a non-instantiable utility class.
 *
 * <p><b>Core methods:</b>
 * <ul>
 *   <li>{@link #playRemoteUseSound(ServerPlayer, ServerLevel, Entity, BlockPos, ItemStack)} —
 *       Selects and plays the appropriate remote use sound based on item type (e.g., hoe tilling, shovel flattening, axe stripping)</li>
 *   <li>{@link #sendDirectSound(ServerPlayer, SoundEvent, SoundSource, double, double, double, float, float)} —
 *       Sends a {@link ClientboundSoundPacket} directly to the player, supporting custom volume, pitch, and position</li>
 *   <li>{@link #selectRemoteUseSound(ItemStack)} — Selects the corresponding {@link SoundEvent} based on the item stack:
 *       <ul>
 *         <li>{@link HoeItem} → {@link SoundEvents#HOE_TILL}</li>
 *         <li>{@link ShovelItem} → {@link SoundEvents#SHOVEL_FLATTEN}</li>
 *         <li>{@link AxeItem} → {@link SoundEvents#AXE_STRIP}</li>
 *         <li>{@link ShearsItem} → {@link SoundEvents#SHEEP_SHEAR}</li>
 *         <li>{@link BoneMealItem} → {@link SoundEvents#BONE_MEAL_USE}</li>
 *         <li>{@code Items.HONEYCOMB} → {@link SoundEvents#HONEYCOMB_WAX_ON}</li>
 *       </ul>
 *   </li>
 *   <li>{@link #createSoundStack(String)} — Constructs an ItemStack for sound playback from an item ID</li>
 * </ul>
 *
 * <p><b>Sound positioning:</b> Uses the entity's bounding box center as the sound source when a target entity exists,
 * otherwise uses the block center, ensuring accurate sound positioning during remote operations.
 */
public final class SoundService {

    private SoundService() {
    }

    public static void playRemoteUseSound(ServerPlayer player, ServerLevel level, Entity targetEntity, BlockPos pos,
            ItemStack stack) {
        if (player == null || level == null || stack == null || stack.isEmpty()) {
            return;
        }
        SoundEvent sound = selectRemoteUseSound(stack);
        if (sound == null) {
            return;
        }
        SoundSource source = targetEntity == null ? SoundSource.BLOCKS : SoundSource.PLAYERS;
        Vec3 at = targetEntity == null
                ? new Vec3(
                        pos == null ? player.getX() : pos.getX() + 0.5D,
                        pos == null ? player.getY() : pos.getY() + 0.5D,
                        pos == null ? player.getZ() : pos.getZ() + 0.5D)
                : targetEntity.getBoundingBox().getCenter();
        sendDirectSound(player, sound, source, at.x, at.y, at.z, 1.0F, 1.0F);
    }

    public static void sendDirectSound(ServerPlayer player, SoundEvent sound, SoundSource source, double x, double y,
            double z, float volume, float pitch) {
        if (player == null || sound == null || sound == SoundEvents.EMPTY) {
            return;
        }
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                source,
                x, y, z,
                volume, pitch,
                player.getRandom().nextLong()));
    }

    /**
     * Selects the corresponding remote use sound based on the item stack.
     */
    static SoundEvent selectRemoteUseSound(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof HoeItem) {
            return SoundEvents.HOE_TILL;
        }
        if (item instanceof ShovelItem) {
            return SoundEvents.SHOVEL_FLATTEN;
        }
        if (item instanceof AxeItem) {
            return SoundEvents.AXE_STRIP;
        }
        if (item instanceof ShearsItem) {
            return SoundEvents.SHEEP_SHEAR;
        }
        if (item instanceof BoneMealItem) {
            return SoundEvents.BONE_MEAL_USE;
        }
        if (item == Items.HONEYCOMB) {
            return SoundEvents.HONEYCOMB_WAX_ON;
        }
        return null;
    }

    /**
     * Constructs an ItemStack for sound playback from the given item ID.
     */
    public static ItemStack createSoundStack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
