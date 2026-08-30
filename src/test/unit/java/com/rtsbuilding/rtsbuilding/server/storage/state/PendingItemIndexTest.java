package com.rtsbuilding.rtsbuilding.server.storage.state;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingItemIndexTest {
    @Test
    void unrelatedStorageChangesDoNotReturnPendingJobs() {
        PendingItemIndex<String> index = new PendingItemIndex<>();
        index.add("minecraft:oak_planks", "oak-job");
        index.add("minecraft:stone", "stone-job");

        assertEquals(List.of(), index.valuesFor(List.of("minecraft:dirt")));
        assertEquals(List.of("stone-job"), index.valuesFor(List.of("minecraft:stone")));
    }

    @Test
    void removalDropsOnlyTheMatchingJob() {
        PendingItemIndex<String> index = new PendingItemIndex<>();
        index.add("minecraft:stone", "first");
        index.add("minecraft:stone", "second");
        index.remove("minecraft:stone", "first");

        assertEquals(List.of("second"), index.valuesFor(List.of("minecraft:stone")));
    }
}
