package com.rtsbuilding.rtsbuilding.energy.block;

import com.mojang.serialization.MapCodec;
import com.rtsbuilding.rtsbuilding.energy.RtsEnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsThermalGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Thermal generator — burns lava to produce FE for the owner's energy grid.
 * <p>
 * The {@code LIT} state property drives the on/off model; the block stays lit
 * while it has fuel and room to store the generated energy. Its internal energy
 * buffer is part of the player's grid. The block can be oriented horizontally
 * via the {@code FACING} state property.
 */
public class RtsThermalGeneratorBlock extends RtsEnergyBlock implements EntityBlock {

    public static final MapCodec<RtsThermalGeneratorBlock> CODEC = simpleCodec(RtsThermalGeneratorBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public RtsThermalGeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties, "models/block/thermal_generator_off.json");
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected Direction shapeFacing(BlockState state) {
        return state.getValue(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RtsThermalGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide && type == RtsEnergyBlockEntities.THERMAL_GENERATOR.get()) {
            return (lvl, pos, blockState, be) -> ((RtsThermalGeneratorBlockEntity) be).tickServer();
        }
        return null;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (level.getBlockEntity(pos) instanceof RtsThermalGeneratorBlockEntity generator) {
            return generator.interactWithBucket(stack, player);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
