package com.rtsbuilding.rtsbuilding.client.rendering.state;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsWorldPreviewSnapshotTest {
    @Test
    void snapshotDefensivelyCopiesTheExtractedList() {
        var source = new ArrayList<RtsWorldPreviewSnapshot.ModelGhost>();
        RtsWorldPreviewSnapshot snapshot = new RtsWorldPreviewSnapshot(source);
        source.clear();

        assertTrue(snapshot.modelGhosts().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.modelGhosts().add(null));
    }
}
