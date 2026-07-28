package com.rtsbuilding.rtsbuilding.server.culling;

import com.rtsbuilding.rtsbuilding.network.culling.RtsCullingBoxSnapshot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCullingPersistenceTest {
    @Test
    void savesAndDimensionsKeepIndependentCullingRecords() {
        NBTTagCompound saveA = new NBTTagCompound();
        NBTTagCompound saveB = new NBTTagCompound();
        RtsCullingBoxSnapshot overworldA = box(1, 64, 1);
        RtsCullingBoxSnapshot netherA = box(2, 70, 2);
        RtsCullingBoxSnapshot overworldB = box(99, 80, 99);

        RtsCullingPersistence.encode(saveA, "minecraft:overworld", List.of(overworldA), List.of(BlockPos.ORIGIN));
        RtsCullingPersistence.encode(saveA, "minecraft:the_nether", List.of(netherA), List.of());
        RtsCullingPersistence.encode(saveB, "minecraft:overworld", List.of(overworldB), List.of());

        assertEquals(List.of(overworldA),
                RtsCullingPersistence.decode(saveA, "minecraft:overworld").boxes());
        assertEquals(List.of(netherA),
                RtsCullingPersistence.decode(saveA, "minecraft:the_nether").boxes());
        assertEquals(List.of(overworldB),
                RtsCullingPersistence.decode(saveB, "minecraft:overworld").boxes());
        assertTrue(RtsCullingPersistence.decode(saveB, "minecraft:the_nether").boxes().isEmpty());
        assertEquals(List.of(BlockPos.ORIGIN),
                RtsCullingPersistence.decode(saveA, "minecraft:overworld").revealed());
    }

    private static RtsCullingBoxSnapshot box(int x, int y, int z) {
        return new RtsCullingBoxSnapshot(
                new BlockPos(x, y, z),
                new BlockPos(x + 3, y + 4, z + 5));
    }
}
