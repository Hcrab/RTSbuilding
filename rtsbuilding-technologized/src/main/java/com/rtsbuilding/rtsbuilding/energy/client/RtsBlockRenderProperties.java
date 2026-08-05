package com.rtsbuilding.rtsbuilding.energy.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

/**
 * Client block extensions for the energy blocks.
 * <p>
 * Vanilla spawns a separate burst of break particles for <em>every</em> AABB in
 * a block's collision/selection shape, so multi-element models (e.g. a
 * generator) produce a huge cloud of particles when broken. Modeled after
 * Mekanism's {@code RenderPropertiesProvider.PARTICLE_HANDLER}: this override
 * consolidates the destroy effect to the shape's overall bounding box, spawning
 * a single normal-sized burst regardless of how many components the shape has.
 */
public final class RtsBlockRenderProperties {

    public static final IClientBlockExtensions INSTANCE = new IClientBlockExtensions() {
        @Override
        public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
            VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty()) {
                return false;
            }
            AABB bounds = shape.bounds();
            double xDif = Math.min(1.0, bounds.maxX - bounds.minX);
            double yDif = Math.min(1.0, bounds.maxY - bounds.minY);
            double zDif = Math.min(1.0, bounds.maxZ - bounds.minZ);
            int xCount = Math.max(1, Mth.ceil(xDif / 0.25));
            int yCount = Math.max(1, Mth.ceil(yDif / 0.25));
            int zCount = Math.max(1, Mth.ceil(zDif / 0.25));
            for (int x = 0; x < xCount; x++) {
                for (int y = 0; y < yCount; y++) {
                    for (int z = 0; z < zCount; z++) {
                        double d4 = (x + 0.5) / xCount;
                        double d5 = (y + 0.5) / yCount;
                        double d6 = (z + 0.5) / zCount;
                        double d7 = d4 * xDif + bounds.minX;
                        double d8 = d5 * yDif + bounds.minY;
                        double d9 = d6 * zDif + bounds.minZ;
                        manager.add(new TerrainParticle((ClientLevel) level, pos.getX() + d7, pos.getY() + d8,
                                pos.getZ() + d9, d4 - 0.5, d5 - 0.5, d6 - 0.5, state).updateSprite(state, pos));
                    }
                }
            }
            return true;
        }
    };

    private RtsBlockRenderProperties() {
    }
}
