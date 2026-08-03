package com.rtsbuilding.rtsbuilding.network.craft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证普通机器 JEI 转移包的往返编码和拒绝边界。 */
class RtsJeiContainerTransferPayloadTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

    @Test
    void roundTripPreservesSparseSlotsAndAlternativeIdentity() {
        C2SRtsJeiContainerTransferPayload source =
                new C2SRtsJeiContainerTransferPayload(
                        17,
                        Arrays.asList(1, 4, 7),
                        Arrays.asList(
                                Collections.singletonList(new ItemStack(Blocks.COBBLESTONE)),
                                Arrays.asList(new ItemStack(Items.IRON_INGOT),
                                        new ItemStack(Items.GOLD_INGOT)),
                                Collections.singletonList(new ItemStack(Blocks.PLANKS, 1, 2))),
                        true,
                        true);

        ByteBuf buffer = Unpooled.buffer();
        try {
            source.toBytes(buffer);
            C2SRtsJeiContainerTransferPayload decoded =
                    new C2SRtsJeiContainerTransferPayload();
            decoded.fromBytes(buffer);

            assertTrue(decoded.isValid());
            assertEquals(Arrays.asList(1, 4, 7), decoded.targetSlots());
            assertEquals(2, decoded.alternatives().get(1).size());
            assertEquals(2, decoded.alternatives().get(2).get(0).getMetadata());
            assertTrue(decoded.maxTransfer());
            assertTrue(decoded.requireCompleteSets());
        } finally {
            buffer.release();
        }
    }

    @Test
    void duplicateAndZeroWindowTargetsAreRejectedBeforeEncoding() {
        C2SRtsJeiContainerTransferPayload duplicate =
                new C2SRtsJeiContainerTransferPayload(
                        4,
                        Arrays.asList(2, 2),
                        Arrays.asList(
                                Collections.singletonList(new ItemStack(Items.STICK)),
                                Collections.singletonList(new ItemStack(Items.STICK))),
                        false,
                        true);
        C2SRtsJeiContainerTransferPayload playerInventoryWindow =
                new C2SRtsJeiContainerTransferPayload(
                        0,
                        Collections.singletonList(1),
                        Collections.singletonList(
                                Collections.singletonList(new ItemStack(Items.STICK))),
                        false,
                        true);

        assertFalse(duplicate.isValid());
        assertFalse(playerInventoryWindow.isValid());
        ByteBuf buffer = Unpooled.buffer();
        try {
            assertThrows(IllegalArgumentException.class, () -> duplicate.toBytes(buffer));
        } finally {
            buffer.release();
        }
    }
}
