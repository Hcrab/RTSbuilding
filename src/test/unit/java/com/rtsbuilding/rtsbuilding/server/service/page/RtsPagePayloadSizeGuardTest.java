package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePagePayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPagePayloadSizeGuardTest {
    @Test
    void searchResponsesOmitFullTotalCountSnapshots() {
        assertTrue(RtsPageCore.shouldSendTotalCountsSnapshot(""));
        assertTrue(RtsPageCore.shouldSendTotalCountsSnapshot("   "));
        assertFalse(RtsPageCore.shouldSendTotalCountsSnapshot("diamond"),
                "搜索每次按键刷新时不应重复发送完整库存总计。");
    }

    @Test
    void localizedSearchMatchesAreCappedBeforeNetworkEncoding() {
        List<String> matches = new ArrayList<>();
        int oversized = C2SRtsRequestStoragePagePayload.MAX_LOCALIZED_SEARCH_MATCHES + 50;
        for (int i = 0; i < oversized; i++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "item_" + i);
            matches.add(id.toString());
        }

        List<String> limited = C2SRtsRequestStoragePagePayload.limitLocalizedSearchMatches(matches);

        assertEquals(C2SRtsRequestStoragePagePayload.MAX_LOCALIZED_SEARCH_MATCHES, limited.size());
        assertEquals("example:item_0", limited.get(0));
        assertEquals("example:item_" + (C2SRtsRequestStoragePagePayload.MAX_LOCALIZED_SEARCH_MATCHES - 1),
                limited.get(limited.size() - 1));
    }
}
