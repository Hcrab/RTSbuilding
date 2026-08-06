package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.storage.StackCompat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 1.7.10 以 null 表示空 ItemStack 的网络语义。 */
class NullableItemStackPayloadTest {
    @Test
    void singleMineStartAndAbortPreserveEmptyTool() {
        assertEmptyTool(roundTrip(
                new C2SRtsMinePayload(new BlockPos(1, 64, 1), (byte) 1, true,
                        (byte) 0, "", null, false, false),
                new C2SRtsMinePayload()));
        assertEmptyTool(roundTrip(
                new C2SRtsMinePayload(new BlockPos(1, 64, 1), (byte) 1, false,
                        (byte) 0, "", null, false, false),
                new C2SRtsMinePayload()));
    }

    @Test
    void everyMiningPayloadAcceptsEmptyTool() {
        C2SRtsUltiminePayload ultimine = roundTrip(
                new C2SRtsUltiminePayload(new BlockPos(2, 64, 2), (byte) 1,
                        (byte) 0, "", null, (short) 32, (byte) 0, false),
                new C2SRtsUltiminePayload());
        assertTrue(ultimine.isValid());
        assertNull(ultimine.toolPrototype());

        C2SRtsAreaMinePayload areaMine = roundTrip(
                new C2SRtsAreaMinePayload(0, 1, 63, 64, 0, 1,
                        (byte) 0, "", null, (byte) 0, (byte) 0, false),
                new C2SRtsAreaMinePayload());
        assertTrue(areaMine.isValid());
        assertNull(areaMine.toolPrototype());

        C2SRtsAreaDestroyPayload areaDestroy = roundTrip(
                new C2SRtsAreaDestroyPayload(
                        Collections.singletonList(new BlockPos(3, 64, 3)),
                        (byte) 0, "", null, false),
                new C2SRtsAreaDestroyPayload());
        assertTrue(areaDestroy.isValid());
        assertNull(areaDestroy.toolPrototype());
        assertDoesNotThrow(areaDestroy::metadataSignature);
    }

    @Test
    void emptyHandPlacementAndBatchMetadataRemainNullSafe() {
        C2SRtsPlacePayload place = roundTrip(
                new C2SRtsPlacePayload(new BlockPos(4, 64, 4), (byte) 1,
                        0.5D, 0.5D, 0.5D, (byte) 0, "", false, false,
                        "", null, 4.5D, 65.0D, 4.5D, 0.0D, -1.0D, 0.0D,
                        false, true),
                new C2SRtsPlacePayload());
        assertTrue(place.isValid());
        assertNull(place.itemPrototype());

        C2SRtsPlaceBatchPayload batch = roundTrip(
                new C2SRtsPlaceBatchPayload(
                        Collections.singletonList(new BlockPos(5, 64, 5)), (byte) 1,
                        0.5D, 0.5D, 0.5D, (byte) 0, "", false, false,
                        false, "", null, 5.5D, 65.0D, 5.5D, 0.0D, -1.0D, 0.0D),
                new C2SRtsPlaceBatchPayload());
        assertTrue(batch.isValid());
        assertNull(batch.itemPrototype());
        assertDoesNotThrow(batch::metadataSignature);
    }

    @Test
    void pinnedInteractionNormalizesAndPreservesMetadataAndNbtPrototype() {
        Item testItem = new Item();
        ItemStack prototype = new ItemStack(testItem, 64, 4);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("rts-test", "variant");
        prototype.setTagCompound(tag);

        C2SRtsInteractPayload payload = new C2SRtsInteractPayload(
                23L, C2SRtsInteractPayload.NO_ENTITY, new BlockPos(8, 64, 8),
                (byte) 1, 8.5D, 64.5D, 8.5D,
                C2SRtsInteractPayload.SOURCE_PIN_ITEM, (byte) 0,
                "rtsbuilding:payload_test", prototype,
                8.5D, 66.0D, 8.5D, 0.0D, -1.0D, 0.0D);

        ItemStack decoded = payload.itemPrototype();
        assertNotNull(decoded);
        assertEquals(1, decoded.stackSize);
        assertEquals(4, decoded.getItemDamage());
        assertNotNull(decoded.getTagCompound());
        assertEquals("variant", decoded.getTagCompound().getString("rts-test"));
    }

    private static void assertEmptyTool(C2SRtsMinePayload payload) {
        assertTrue(payload.isValid());
        assertNull(payload.toolPrototype());
        assertNull(StackCompat.copyOrNull(payload.toolPrototype()));
    }

    private static <T extends cpw.mods.fml.common.network.simpleimpl.IMessage> T roundTrip(
            T source, T decoded) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            source.toBytes(buffer);
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
