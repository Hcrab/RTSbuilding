package com.rtsbuilding.rtsbuilding.platform.player;

import com.rtsbuilding.rtsbuilding.platform.interaction.EnumHand;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** 玩家背包与手持物的跨版本入口；1.7.10 明确只支持主手。 */
public final class PlayerCompat {
    private PlayerCompat() {}

    public static ItemStack getHeldItem(EntityPlayer player) {
        return player == null ? null : player.getHeldItem();
    }

    public static ItemStack getHeldItem(EntityPlayer player, EnumHand hand) {
        return player == null || hand != EnumHand.MAIN_HAND ? null : player.getHeldItem();
    }

    public static void setHeldItem(EntityPlayer player, EnumHand hand, ItemStack stack) {
        if (player == null || hand != EnumHand.MAIN_HAND) return;
        player.inventory.setInventorySlotContents(player.inventory.currentItem, stack);
        player.inventory.markDirty();
    }

    public static Vec3d position(Entity entity) {
        return entity == null ? Vec3d.ZERO : new Vec3d(entity.posX, entity.posY, entity.posZ);
    }

    /** 后续版本无参 getPosition 的等价物；明确按脚部坐标向下取整。 */
    public static BlockPos blockPosition(Entity entity) {
        return entity == null ? BlockPos.ORIGIN : new BlockPos(entity);
    }

    public static String name(EntityPlayer player) {
        return player == null ? "" : player.getCommandSenderName();
    }

    public static boolean canUseCommand(EntityPlayer player, int level, String command) {
        return player != null && player.canCommandSenderUseCommand(level, command == null ? "" : command);
    }

    /** 1.7.10 没有旁观模式；保留调用点语义并始终返回 false。 */
    public static boolean isSpectator(EntityPlayer player) {
        return false;
    }

    public static boolean hasConnection(EntityPlayerMP player) {
        return player != null && player.playerNetServerHandler != null;
    }

    public static Vec3d positionEyes(Entity entity, float partialTicks) {
        if (entity == null) return Vec3d.ZERO;
        double x = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
        double y = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks
                + entity.getEyeHeight();
        double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;
        return new Vec3d(x, y, z);
    }

    public static double distanceSqToCenter(Entity entity, BlockPos pos) {
        if (entity == null || pos == null) return Double.POSITIVE_INFINITY;
        double dx = entity.posX - (pos.getX() + 0.5D);
        double dy = entity.posY - (pos.getY() + 0.5D);
        double dz = entity.posZ - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    public static Vec3d look(Entity entity, float partialTicks) {
        if (entity instanceof EntityLivingBase) {
            return Vec3d.fromNative(((EntityLivingBase) entity).getLook(partialTicks));
        }
        return entity == null ? Vec3d.ZERO : Vec3d.fromNative(entity.getLookVec());
    }
}
