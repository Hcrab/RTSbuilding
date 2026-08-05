package com.rtsbuilding.rtsbuilding.energy.block;

import com.mojang.serialization.MapCodec;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsEnergyBankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Energy bank — a block-level FE storage buffer that participates in the
 * player's energy grid and exposes a standard {@code IEnergyStorage} capability
 * so other mods can charge/discharge it.
 */
public class RtsEnergyBankBlock extends RtsEnergyBlock implements EntityBlock {

    public static final MapCodec<RtsEnergyBankBlock> CODEC = simpleCodec(RtsEnergyBankBlock::new);

    public RtsEnergyBankBlock(BlockBehaviour.Properties properties) {
        super(properties, "models/block/energy_bank.json");
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RtsEnergyBankBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }
}
