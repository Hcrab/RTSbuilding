package com.rtsbuilding.rtsbuilding.energy.block;

import com.rtsbuilding.rtsbuilding.common.geometry.RtsModelShapeParser;
import com.rtsbuilding.rtsbuilding.energy.RtsEnergyMod;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsEnergyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Base block for all energy blocks of the {@code rtsbuilding_technologized}
 * addon. Records the placing player as the energy node owner so the server can
 * attribute the block to that player's energy grid.
 * <p>
 * Collision/selection boxes are generated automatically from the block's model
 * JSON via {@link RtsModelShapeParser}. Shapes are cached per facing, so the
 * hot-path {@link #getShape} / {@link #getCollisionShape} calls reduce to a
 * single cached lookup with no parsing or synchronization; subclasses that have
 * a facing property override {@link #shapeFacing(BlockState)} to rotate the
 * shape accordingly.
 */
public abstract class RtsEnergyBlock extends Block {

    @Nullable
    private final RtsModelShapeParser.CachedShapeGenerator shapeGenerator;

    /**
     * @param modelPath The model path (relative to {@code assets/rtsbuilding_technologized/}) used
     *                  to generate this block's collision/selection shape, or
     *                  {@code null} to keep the default full-cube shape.
     */
    protected RtsEnergyBlock(Properties properties, @Nullable String modelPath) {
        super(properties);
        this.shapeGenerator = modelPath == null ? null
                : new RtsModelShapeParser.CachedShapeGenerator(RtsEnergyMod.MODID, modelPath);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
          @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            if (level.getBlockEntity(pos) instanceof RtsEnergyBlockEntity energyBe) {
                energyBe.setOwner(player.getUUID());
            }
        }
    }

    @Override
    public final VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeGenerator == null ? super.getShape(state, level, pos, context) : shapeFor(state);
    }

    @Override
    public final VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeGenerator == null ? super.getCollisionShape(state, level, pos, context) : shapeFor(state);
    }

    private VoxelShape shapeFor(BlockState state) {
        return shapeGenerator.getShape(shapeFacing(state));
    }

    /**
     * The facing used to look up the model-derived shape.
     *
     * @return The facing; defaults to {@link Direction#NORTH} for blocks without
     *         a facing state property.
     */
    protected Direction shapeFacing(BlockState state) {
        return Direction.NORTH;
    }
}
