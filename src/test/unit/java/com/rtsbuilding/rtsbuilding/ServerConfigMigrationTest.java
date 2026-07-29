package com.rtsbuilding.rtsbuilding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerConfigMigrationTest {
    @Test
    void migratesBothPublishedLegacyThroughputDefaults() {
        ServerConfigMigration.Values fromEight =
                ServerConfigMigration.migrate(0, 8, 4_000_000L);
        ServerConfigMigration.Values fromSixteen =
                ServerConfigMigration.migrate(0, 16, 4_000_000L);

        assertEquals(1, fromEight.revision());
        assertEquals(32, fromEight.miningSlice());
        assertEquals(8_000_000L, fromEight.taskBudgetNanos());
        assertEquals(32, fromSixteen.miningSlice());
        assertEquals(8_000_000L, fromSixteen.taskBudgetNanos());
    }

    @Test
    void preservesExplicitCustomValues() {
        ServerConfigMigration.Values values =
                ServerConfigMigration.migrate(0, 48, 6_500_000L);

        assertEquals(1, values.revision());
        assertEquals(48, values.miningSlice());
        assertEquals(6_500_000L, values.taskBudgetNanos());
    }

    @Test
    void completedMigrationIsIdempotent() {
        ServerConfigMigration.Values values =
                ServerConfigMigration.migrate(1, 8, 4_000_000L);

        assertEquals(1, values.revision());
        assertEquals(8, values.miningSlice());
        assertEquals(4_000_000L, values.taskBudgetNanos());
    }
}
