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
 * 远程交互声音服务。
 *
 * <p>封装 RTS 模式下远程操作方块/实体后播放声音的逻辑，
 * 包括声音选择、声音包发送、以及声音栈构造。
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
     * 根据物品栈选择对应的远程使用声音。
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
     * 根据物品 ID 构造用于声音播放的 ItemStack。
     */
    public static ItemStack createSoundStack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
