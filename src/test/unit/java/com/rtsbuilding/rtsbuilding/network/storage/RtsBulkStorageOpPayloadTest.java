package com.rtsbuilding.rtsbuilding.network.storage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBulkStorageOpPayloadTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

    @Test
    void roundTripsExactWithdrawPrototypeAndAmount() {
        ItemStack prototype = new ItemStack(Items.DIAMOND_PICKAXE);
        prototype.setStackDisplayName("named");
        C2SRtsBulkStorageOpPayload encoded = new C2SRtsBulkStorageOpPayload(
                C2SRtsBulkStorageOpPayload.WITHDRAW, prototype, 64);
        ByteBuf buffer = Unpooled.buffer();
        encoded.toBytes(buffer);

        C2SRtsBulkStorageOpPayload decoded = new C2SRtsBulkStorageOpPayload();
        decoded.fromBytes(buffer);
        assertEquals(C2SRtsBulkStorageOpPayload.WITHDRAW, decoded.action());
        assertEquals(64, decoded.amount());
        assertEquals(1, decoded.prototype().getCount());
        assertTrue(ItemStack.areItemStackTagsEqual(prototype, decoded.prototype()));
    }

    @Test
    void acceptsEmptyPrototypeForDepositAll() {
        assertTrue(new C2SRtsBulkStorageOpPayload(
                C2SRtsBulkStorageOpPayload.DEPOSIT_ALL, ItemStack.EMPTY, 0).isValid());
    }
}
