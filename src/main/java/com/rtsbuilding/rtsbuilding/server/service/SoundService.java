package com.rtsbuilding.rtsbuilding.server.service;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * 远程交互声音服务——封装 RTS 模式下远程操作的声音播放逻辑。
 *
 * <p>此服务处理在远程相机模式下与方块/实体交互后的声音反馈，
 * 包括声音选择、网络包发送和 ItemStack 构造。
 * 所有方法均为 {@code static}，类本身为不可实例化的工具类。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #playRemoteUseSound(EntityPlayerMP, WorldServer, Entity, BlockPos, ItemStack)} —
 *       根据物品类型选择对应的远程使用声音并播放（如锄头耕地、锹铲平、斧剥皮等）</li>
 *   <li>{@link #sendDirectSound(EntityPlayerMP, SoundEvent, SoundCategory, double, double, double, float, float)} —
 *       直接向玩家发送 {@link SPacketSoundEffect}，支持自定义音量、音调和位置</li>
 *   <li>{@link #selectRemoteUseSound(ItemStack)} — 根据物品栈选择对应的 {@link SoundEvent}：
 *       <ul>
 *         <li>{@link ItemHoe} → {@link SoundEvents#ITEM_HOE_TILL}</li>
 *         <li>{@link ItemSpade} → {@link SoundEvents#ITEM_SHOVEL_FLATTEN}</li>
 *         <li>{@link ItemShears} → {@link SoundEvents#ENTITY_SHEEP_SHEAR}</li>
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

    public static void playRemoteUseSound(EntityPlayerMP player, WorldServer level, Entity targetEntity, BlockPos pos,
            ItemStack stack) {
        if (player == null || level == null || stack == null || stack.isEmpty()) {
            return;
        }
        SoundEvent sound = selectRemoteUseSound(stack);
        if (sound == null) {
            return;
        }
        SoundCategory source = targetEntity == null ? SoundCategory.BLOCKS : SoundCategory.PLAYERS;
        AxisAlignedBB bounds = targetEntity == null ? null : targetEntity.getEntityBoundingBox();
        Vec3d at = targetEntity == null
                ? new Vec3d(
                        pos == null ? player.posX : pos.getX() + 0.5D,
                        pos == null ? player.posY : pos.getY() + 0.5D,
                        pos == null ? player.posZ : pos.getZ() + 0.5D)
                : new Vec3d((bounds.minX + bounds.maxX) * 0.5D,
                        (bounds.minY + bounds.maxY) * 0.5D,
                        (bounds.minZ + bounds.maxZ) * 0.5D);
        sendDirectSound(player, sound, source, at.x, at.y, at.z, 1.0F, 1.0F);
    }

    public static void sendDirectSound(EntityPlayerMP player, SoundEvent sound, SoundCategory source, double x, double y,
            double z, float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }
        player.connection.sendPacket(new SPacketSoundEffect(sound, source, x, y, z, volume, pitch));
    }

    /**
     * 根据物品栈选择对应的远程使用声音。
     */
    static SoundEvent selectRemoteUseSound(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ItemHoe) {
            return SoundEvents.ITEM_HOE_TILL;
        }
        if (item instanceof ItemSpade) {
            return SoundEvents.ITEM_SHOVEL_FLATTEN;
        }
        if (item instanceof ItemShears) {
            return SoundEvents.ENTITY_SHEEP_SHEAR;
        }
        // 1.12.2 尚无原版剥皮斧、蜂蜡和骨粉使用 SoundEvent；不能伪造错误音效。
        if (item instanceof ItemAxe) return null;
        return null;
    }

    /**
     * 根据物品 ID 构造用于声音播放的 ItemStack。
     */
    public static ItemStack createSoundStack(String itemId) {
        ResourceLocation id = parseId(itemId);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(ForgeRegistries.ITEMS.getValue(id));
    }

    private static ResourceLocation parseId(String itemId) {
        try {
            return itemId == null || itemId.trim().isEmpty() ? null : new ResourceLocation(itemId);
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
