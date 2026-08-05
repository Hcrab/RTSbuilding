package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.block.material.Material;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.SharedMonsterAttributes;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Forge 1.12 客户端内置移动模式。 */
@SideOnly(Side.CLIENT)
public final class BuiltinMovementModes {
    private BuiltinMovementModes() {
    }

    /** 鞘翅依旧交给原版移动输入和滑翔物理，只负责朝向目标。 */
    static final MovementModeHandler ELYTRA = new MovementModeHandler() {
        @Override
        public boolean isActive(EntityPlayerSP player) {
            // 鞘翅直到 1.9 才加入；保留模式槽位，1.7.10 永不激活。
            return false;
        }

        @Override
        public MovementParams computeParams(EntityPlayerSP player, Vec3d toTarget,
                                            double horizontalDist) {
            return new MovementParams(0.0D, true, false, false, false,
                    MovementParams.StuckBehavior.FLY_UP, true, true);
        }
    };

    /** 原版创造飞行及通过 PlayerCapabilities 暴露飞行状态的第三方实现。 */
    static final MovementModeHandler FLYING = new MovementModeHandler() {
        @Override
        public boolean isActive(EntityPlayerSP player) {
            return player.capabilities.isFlying;
        }

        @Override
        public MovementParams computeParams(EntityPlayerSP player, Vec3d toTarget,
                                            double horizontalDist) {
            double speed = player.capabilities.getFlySpeed() * 4.5D;
            return new MovementParams(speed, false, false, false, false,
                    MovementParams.StuckBehavior.FLY_UP, false, true);
        }
    };

    /**
     * 1.12 没有新版原生游泳姿态，因此以眼部浸液或实体处于液体内部作为可观测依据。
     * 浅水只湿到脚时仍交给步行模式，岩浆内离地则采用三维游动。
     */
    static final MovementModeHandler SWIMMING = new MovementModeHandler() {
        @Override
        public boolean isActive(EntityPlayerSP player) {
            boolean submergedInWater = player.isInWater()
                    && (player.isInsideOfMaterial(Material.water)
                    || materialAtEyes(player) == Material.water);
            return submergedInWater || (player.handleLavaMovement() && !player.onGround);
        }

        @Override
        public MovementParams computeParams(EntityPlayerSP player, Vec3d toTarget,
                                            double horizontalDist) {
            double speed = getMovementSpeed(player) * 1.6D;
            return new MovementParams(speed, true, true, false, false,
                    MovementParams.StuckBehavior.FLOAT_UP);
        }
    };

    /**
     * 1.12 原版没有爬行姿态。对第三方爬行实现采用保守策略：仅当地面玩家
     * 的真实碰撞箱已缩到 1.55 格以下，或站立高度探针确实被顶棚阻挡时启用。
     */
    static final MovementModeHandler CRAWLING = new MovementModeHandler() {
        @Override
        public boolean isActive(EntityPlayerSP player) {
            if (!player.onGround || player.isInWater() || player.handleLavaMovement()) return false;
            AxisAlignedBB box = com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB.fromNative(player.boundingBox);
            boolean compactBody = box.maxY - box.minY <= 1.55D || player.height <= 1.55F;
            return compactBody || hasLowHeadroom(player, box);
        }

        @Override
        public MovementParams computeParams(EntityPlayerSP player, Vec3d toTarget,
                                            double horizontalDist) {
            double speed = computeGroundSpeed(player, 0.3D) * getFluidSlowFactor(player);
            return new MovementParams(speed, false, false, true, true,
                    MovementParams.StuckBehavior.JUMP);
        }
    };

    /** 始终可用的地面兜底模式。 */
    static final MovementModeHandler WALKING = new MovementModeHandler() {
        @Override
        public boolean isActive(EntityPlayerSP player) {
            return true;
        }

        @Override
        public MovementParams computeParams(EntityPlayerSP player, Vec3d toTarget,
                                            double horizontalDist) {
            double speed = computeGroundSpeed(player, 1.0D) * getFluidSlowFactor(player);
            return new MovementParams(speed, false, true, true, true,
                    MovementParams.StuckBehavior.JUMP);
        }
    };

    private static double computeGroundSpeed(EntityPlayerSP player, double multiplier) {
        float friction = 0.6F;
        if (player.onGround) {
            BlockPos below = new BlockPos(player.posX,
                    com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB.fromNative(player.boundingBox).minY - 0.01D, player.posZ);
            BlockState state = BlockState.fromWorld(player.worldObj, below);
            friction = state.getBlock().slipperiness;
        }
        if (friction <= 0.0F) friction = 0.6F;
        return getMovementSpeed(player) * 2.15D * (0.6D / friction) * multiplier;
    }

    private static double getMovementSpeed(EntityPlayerSP player) {
        return player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                .getAttributeValue();
    }

    private static Material materialAtEyes(EntityPlayerSP player) {
        BlockPos eyePos = new BlockPos(player.posX,
                player.posY + player.getEyeHeight(), player.posZ);
        return BlockState.fromWorld(player.worldObj, eyePos).getMaterial();
    }

    private static boolean hasLowHeadroom(EntityPlayerSP player, AxisAlignedBB box) {
        double inset = 0.05D;
        AxisAlignedBB standingProbe = new AxisAlignedBB(
                box.minX + inset, box.minY + inset, box.minZ + inset,
                box.maxX - inset, box.minY + 1.8D, box.maxZ - inset);
        return !player.worldObj.getCollidingBoundingBoxes(player, standingProbe).isEmpty();
    }

    /** 扫描真实碰撞箱内的液体材质，保留岩浆和普通液体的不同减速。 */
    private static double getFluidSlowFactor(EntityPlayerSP player) {
        AxisAlignedBB box = com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB.fromNative(player.boundingBox);
        BlockPos min = new BlockPos(box.minX, box.minY, box.minZ);
        BlockPos max = new BlockPos(box.maxX, box.maxY, box.maxZ);
        for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(min, max)) {
            Material material = BlockState.fromWorld(player.worldObj, pos).getMaterial();
            if (material == Material.lava) return 0.15D;
            if (material.isLiquid()) return 0.3D;
        }
        return 1.0D;
    }
}
