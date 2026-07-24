package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;


public final class BuiltinMovementModes {

    private BuiltinMovementModes() {
    }

    
    
    

    
    static final MovementModeHandler ELYTRA = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return player.isFallFlying();
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            
            return new MovementParams(0, true, false, false, false,
                    MovementParams.StuckBehavior.FLY_UP, true, true);
        }
    };

    
    
    

    
    static final MovementModeHandler FLYING = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return player.getAbilities().flying;
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            double speed = player.getAbilities().getFlyingSpeed() * 4.5;
            
            return new MovementParams(speed, false, false, false, false,
                    MovementParams.StuckBehavior.FLY_UP, false, true);
        }
    };

    
    
    

    
    static final MovementModeHandler SWIMMING = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return (player.getPose() == Pose.SWIMMING && player.isUnderWater() && player.isInWater())
                    || (player.isInLava() && !player.onGround());
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            double speed = player.getSpeed() * 1.6;
            return new MovementParams(speed, true, true, false, false,
                    MovementParams.StuckBehavior.FLOAT_UP);
        }

        @Override
        public void onActivate(LocalPlayer player) {
            player.setSwimming(true);
        }

        @Override
        public void onDeactivate(LocalPlayer player) {
            player.setSwimming(false);
        }
    };

    
    
    

    
    static final MovementModeHandler CRAWLING = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return player.getPose() == Pose.SWIMMING
                    && player.onGround()
                    && !player.isInWater()
                    && !player.isInLava();
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            double speed = computeGroundSpeed(player, 0.3);
            speed *= getFluidSlowFactor(player);
            return new MovementParams(speed, false, false, true, true,
                    MovementParams.StuckBehavior.JUMP);
        }
    };

    
    
    

    
    static final MovementModeHandler WALKING = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return true; 
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            double speed = computeGroundSpeed(player, 1.0);
            speed *= getFluidSlowFactor(player);
            return new MovementParams(speed, false, true, true, true,
                    MovementParams.StuckBehavior.JUMP);
        }

        @Override
        public void onActivate(LocalPlayer player) {
            player.setSwimming(false);
        }
    };

    
    
    

    
    private static double computeGroundSpeed(LocalPlayer player, double multiplier) {
        float blockFriction = player.onGround()
                ? player.level().getBlockState(player.getOnPos()).getBlock().getFriction()
                : 0.6f;
        return player.getSpeed() * 2.15 * (0.6 / blockFriction) * multiplier;
    }

    
    private static double getFluidSlowFactor(LocalPlayer player) {
        BlockPos min = BlockPos.containing(
                player.getBoundingBox().minX, player.getBoundingBox().minY, player.getBoundingBox().minZ);
        BlockPos max = BlockPos.containing(
                player.getBoundingBox().maxX, player.getBoundingBox().maxY, player.getBoundingBox().maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            FluidState fluidState = player.level().getFluidState(pos);
            if (!fluidState.isEmpty()) {
                if (fluidState.is(FluidTags.LAVA)) return 0.15;
                return 0.3;
            }
        }
        return 1.0;
    }
}
