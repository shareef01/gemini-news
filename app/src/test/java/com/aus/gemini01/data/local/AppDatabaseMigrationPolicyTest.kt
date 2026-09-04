package com.aus.gemini01.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards GN-AUD-011: v4→v5 must remain an additive migration spanning the
 * expected versions. Destructive fallbacks were removed from the builder.
 */
class AppDatabaseMigrationPolicyTest {

    @Test
    fun `migration_4_5_spans_expected_versions`() {
        val migration = AppDatabase.MIGRATION_4_5
        assertEquals(4, migration.startVersion)
        assertEquals(5, migration.endVersion)
    }
}
