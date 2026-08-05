package com.rtsbuilding.rtsbuilding.energy.block.entity;

import com.rtsbuilding.rtsbuilding.api.energy.Action;
import com.rtsbuilding.rtsbuilding.api.energy.AutomationType;
import com.rtsbuilding.rtsbuilding.api.energy.IEnergyContainer;
import com.rtsbuilding.rtsbuilding.common.energy.BasicEnergyContainer;
import com.rtsbuilding.rtsbuilding.energy.RtsEnergyBlockEntities;
import com.rtsbuilding.rtsbuilding.energy.block.RtsThermalGeneratorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Block entity for the thermal generator. Burns lava to produce FE.
 * <p>
 * The internal buffer is part of the owner's energy grid. The block is lit while
 * it has lava and room to store generated energy.
 */
public class RtsThermalGeneratorBlockEntity extends RtsEnergyBlockEntity {

    /** FE generated per tick while burning. */
    public static final long GENERATION_PER_TICK = 60;
    /** Server ticks per millibucket of lava consumed. */
    public static final int TICKS_PER_LAVA_MB = 20;
    /** Internal FE buffer capacity. */
    public static final long BUFFER_CAPACITY = 20_000L;
    /** Lava tank capacity in millibuckets. */
    public static final int TANK_CAPACITY = 8_000;

    private static final String NBT_ENERGY = "energy";
    private static final String NBT_LAVA = "lava";

    private final BasicEnergyContainer buffer = BasicEnergyContainer.create(BUFFER_CAPACITY, this::markChanged);
    private final FluidTank tank = new FluidTank(TANK_CAPACITY, fluid -> fluid.getFluid() == net.minecraft.world.level.material.Fluids.LAVA);

    private int burnTimer;

    public RtsThermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(RtsEnergyBlockEntities.THERMAL_GENERATOR.get(), pos, state);
    }

    private void markChanged() {
        setChanged();
    }

    public FluidTank getTank() {
        return tank;
    }

    /**
     * Handles right-click with a lava bucket (fills the tank) or an empty bucket
     * (drains the tank), swapping buckets in the player's hand.
     */
    public net.minecraft.world.ItemInteractionResult interactWithBucket(ItemStack stack, net.minecraft.world.entity.player.Player player) {
        if (stack.getItem() == net.minecraft.world.item.Items.LAVA_BUCKET && tank.getSpace() >= 1000) {
            if (tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000), FluidAction.EXECUTE) > 0) {
                swapBucket(player, stack, net.minecraft.world.item.Items.BUCKET);
                markChanged();
                return net.minecraft.world.ItemInteractionResult.sidedSuccess(true);
            }
        }
        if (stack.getItem() == net.minecraft.world.item.Items.BUCKET && tank.getFluidAmount() >= 1000) {
            tank.drain(1000, FluidAction.EXECUTE);
            swapBucket(player, stack, net.minecraft.world.item.Items.LAVA_BUCKET);
            markChanged();
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(true);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private void swapBucket(net.minecraft.world.entity.player.Player player, ItemStack inHand,
          net.minecraft.world.item.Item returnItem) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack result = new ItemStack(returnItem);
        if (!player.addItem(result)) {
            player.drop(result, false);
        }
        inHand.shrink(1);
    }

    @Override
    public IEnergyContainer getEnergyBuffer() {
        return buffer;
    }

    @Override
    public long getGeneration() {
        return tank.getFluidAmount() > 0 && buffer.getNeeded() > 0 ? GENERATION_PER_TICK : 0;
    }

    public BasicEnergyContainer getBuffer() {
        return buffer;
    }

    /** Server tick: burn lava, generate FE, and keep the LIT state in sync. */
    public void tickServer() {
        if (level == null || level.isClientSide || !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled()) {
            return;
        }
        boolean burning = tank.getFluidAmount() > 0 && buffer.getNeeded() > 0;
        if (burning) {
            buffer.insert(GENERATION_PER_TICK, Action.EXECUTE, AutomationType.INTERNAL);
            burnTimer++;
            if (burnTimer >= TICKS_PER_LAVA_MB) {
                burnTimer = 0;
                tank.drain(1, FluidAction.EXECUTE);
            }
            markChanged();
        }
        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(RtsThermalGeneratorBlock.LIT) && state.getValue(RtsThermalGeneratorBlock.LIT) != burning) {
            level.setBlock(worldPosition, state.setValue(RtsThermalGeneratorBlock.LIT, burning), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put(NBT_ENERGY, buffer.serializeNBT(provider));
        if (!tank.isEmpty()) {
            tag.put(NBT_LAVA, tank.writeToNBT(provider, new CompoundTag()));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains(NBT_ENERGY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            buffer.deserializeNBT(provider, tag.getCompound(NBT_ENERGY));
        }
        if (tag.contains(NBT_LAVA, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            tank.readFromNBT(provider, tag.getCompound(NBT_LAVA));
        }
    }
}
