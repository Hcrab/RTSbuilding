package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.test.MinecraftTestBootstrapExtension;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证跨维链接操作在网络层保留完整的维度+坐标身份。 */
@ExtendWith(MinecraftTestBootstrapExtension.class)
class C2SLinkedStorageIdentityPayloadTest {

    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");
    private static final BlockPos POSITION = new BlockPos(17, 72, -43);

    @Test
    void unlinkRoundTripKeepsTargetDimension() {
        assertEquals(
                new C2SRtsUnlinkStoragePayload(NETHER, POSITION),
                roundTripUnlink(new C2SRtsUnlinkStoragePayload(NETHER, POSITION)));
    }

    @Test
    void updateRoundTripKeepsTargetDimension() {
        assertEquals(
                new C2SRtsUpdateLinkedStoragePayload(
                        NETHER, POSITION, C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY, 73),
                roundTripUpdate(new C2SRtsUpdateLinkedStoragePayload(
                        NETHER, POSITION, C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY, 73)));
    }

    private static C2SRtsUnlinkStoragePayload roundTripUnlink(C2SRtsUnlinkStoragePayload payload) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer());
        try {
            C2SRtsUnlinkStoragePayload.STREAM_CODEC.encode(buffer, payload);
            return C2SRtsUnlinkStoragePayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static C2SRtsUpdateLinkedStoragePayload roundTripUpdate(C2SRtsUpdateLinkedStoragePayload payload) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer());
        try {
            C2SRtsUpdateLinkedStoragePayload.STREAM_CODEC.encode(buffer, payload);
            return C2SRtsUpdateLinkedStoragePayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
