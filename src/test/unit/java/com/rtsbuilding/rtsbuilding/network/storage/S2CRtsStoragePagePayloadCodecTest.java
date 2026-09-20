package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实 FriendlyByteBuf 上验证储存页完整 payload，不只验证记录字段存在。 */
class S2CRtsStoragePagePayloadCodecTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void richPayloadRoundTripsComponentsLongCountsAndLastPageMetadata() {
        S2CRtsStoragePagePayload payload = payload();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer());
        try {
            S2CRtsStoragePagePayload.STREAM_CODEC.encode(buffer, payload);
            S2CRtsStoragePagePayload decoded = S2CRtsStoragePagePayload.STREAM_CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes(), "payload 解码必须正好消费完整字节流");
            assertEquals(payload.linkedPositions(), decoded.linkedPositions());
            assertEquals(payload.linkedDimensions(), decoded.linkedDimensions());
            assertEquals(payload.itemStacks().size(), decoded.itemStacks().size());
            assertTrue(ItemStack.matches(payload.itemStacks().get(0), decoded.itemStacks().get(0)),
                    "富组件物品身份必须经过真实 ItemStack codec 保留");
            assertEquals(payload.counts(), decoded.counts());
            assertEquals(4_000_000_000L, decoded.counts().get(0));
            assertEquals(payload.totalItemCounts(), decoded.totalItemCounts());
            assertEquals(payload.funnelBufferCounts(), decoded.funnelBufferCounts());
            assertEquals(payload.requestedPage(), decoded.requestedPage());
            assertEquals(payload.effectivePageSize(), decoded.effectivePageSize());
            assertEquals(payload.globalIndex(), decoded.globalIndex());
            assertEquals(payload.serverDataRevision(), decoded.serverDataRevision());
        } finally {
            buffer.release();
        }
    }

    @Test
    void pageWindowKeepsFinalPartialPageBoundary() {
        try (var config = org.mockito.Mockito.mockStatic(com.rtsbuilding.rtsbuilding.Config.class)) {
            config.when(com.rtsbuilding.rtsbuilding.Config::maxStoragePageSize).thenReturn(180);
        var window = com.rtsbuilding.rtsbuilding.server.service.page.RtsPageWindow.calculate(2, 2, 5);
        assertEquals(3, window.totalPages());
        assertEquals(4L, window.globalIndex());
        assertEquals(5, window.toIndex());
        assertEquals(1, window.itemCount());
        }
    }

    private static S2CRtsStoragePagePayload payload() {
        ItemStack rich = new ItemStack(Items.DIAMOND_PICKAXE, 2);
        rich.setDamageValue(11);
        rich.setHoverName(Component.literal("energy-preserve-me"));
        rich.getOrCreateTag().putInt("container_marker", 42);
        ItemStack preview = rich.copyWithCount(1);
        return new S2CRtsStoragePagePayload(
                true, "linked-rich",
                List.of(new BlockPos(1, 64, 2).asLong(), new BlockPos(4, 65, 6).asLong()),
                List.of("minecraft:overworld", "minecraft:the_nether"),
                List.of("overworld", "nether"), List.of((byte) 1, (byte) 2), List.of(3, 7),
                List.of("minecraft:chest", "minecraft:shulker_box"), List.of(true, false),
                2, 3, 5, true, "diamond", "all", (byte) 2, false, true, true,
                List.of("all", "building"), List.of(rich, new ItemStack(Items.SHULKER_BOX)),
                List.of(4_000_000_000L, 123L),
                List.of("minecraft:diamond", "minecraft:stone"), List.of(4_000_000_000L, 5L),
                List.of("minecraft:water"), List.of(6_000_000_000L), List.of(10_000_000_000L),
                List.of("minecraft:diamond", "minecraft:water"), List.of(4L, 8L), List.of(64L, 100L),
                List.of((byte) 0, (byte) 4), List.of("minecraft:diamond", ""),
                List.of(preview, ItemStack.EMPTY), List.of("A", "B"), List.of("minecraft:diamond", ""),
                true, List.of("minecraft:cobblestone"), List.of(9_000_000_000L),
                2, 2, 2, 4L, 5L, 6L, 7L, 8L);
    }
}
