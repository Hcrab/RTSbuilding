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
 * 远程交互声音服务——封装 RTS 模式下远程操作的声音播放逻辑。
 *
 * <p>此服务处理在远程相机模式下与方块/实体交互后的声音反馈，
 * 包括声音选择、网络包发送和 ItemStack 构造。
 * 所有方法均为 {@code static}，类本身为不可实例化的工具类。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #playRemoteUseSound(ServerPlayer, ServerLevel, Entity, BlockPos, ItemStack)} —
 *       根据物品类型选择对应的远程使用声音并播放（如锄头耕地、锹铲平、斧剥皮等）</li>
 *   <li>{@link #sendDirectSound(ServerPlayer, SoundEvent, SoundSource, double, double, double, float, float)} —
 *       直接向玩家发送 {@link ClientboundSoundPacket}，支持自定义音量、音调和位置</li>
 *   <li>{@link #selectRemoteUseSound(ItemStack)} — 根据物品栈选择对应的 {@link SoundEvent}：
 *       <ul>
 *         <li>{@link HoeItem} → {@link SoundEvents#HOE_TILL}</li>
 *         <li>{@link ShovelItem} → {@link SoundEvents#SHOVEL_FLATTEN}</li>
 *         <li>{@link AxeItem} → {@link SoundEvents#AXE_STRIP}</li>
 *         <li>{@link ShearsItem} → {@link SoundEvents#SHEEP_SHEAR}</li>
 *         <li>{@link BoneMealItem} → {@link SoundEvents#BONE_MEAL_USE}</li>
 *         <li>{@code Items.HONEYCOMB} → {@link SoundEvents#HONEYCOMB_WAX_ON}</li>
 *       </ul>
 *   </li>
 *   <li>{@link #createSoundStack(String)} — 根据物品 ID 构造用于声音播放的 ItemStack</li>
 * </ul>
 *
 * <p><b>声音定位：</b>目标实体存在时以实体包围盒中心为音源，
 * 否则以方块中心为音源，确保远程操作时声音位置准确。
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
