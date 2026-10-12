package com.fantasyidler.simulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the deterministic (RNG-free) preview helpers of [SkillSimulator]:
 * `estimateGatheringXp`, `estimateAgilityXp`, and `sessionDurationMs`. The
 * RNG-driven `simulate*` functions are covered separately once a seedable
 * Random seam is introduced.
 */
class SkillSimulatorPureTest {

    @Test
    fun `estimateGatheringXp multiplies per-action XP by efficiency over 60 frames`() {
        assertEquals(600L, SkillSimulator.estimateGatheringXp(10, 1f))
        assertEquals(420L, SkillSimulator.estimateGatheringXp(7, 1f))
        assertEquals(900L, SkillSimulator.estimateGatheringXp(10, 1.5f))
        assertEquals(0L, SkillSimulator.estimateGatheringXp(0, 1f))
    }

    @Test
    fun `estimateAgilityXp grows with level then caps at the 0_95 success rate`() {
        // At the minimum level the success rate is the base 0.80.
        assertEquals(960L, SkillSimulator.estimateAgilityXp(10, 1, 1))
        // Far above the requirement the rate is clamped to 0.95.
        assertEquals(1140L, SkillSimulator.estimateAgilityXp(10, 1, 99))
        // The capped value should equal any level past the cap threshold.
        assertEquals(
            SkillSimulator.estimateAgilityXp(10, 1, 99),
            SkillSimulator.estimateAgilityXp(10, 1, 50),
        )
    }

    @Test
    fun `estimateAgilityXp floors at zero far below the requirement`() {
        // Level 1 on a level 50 course: 0.80 - 0.98 would be a negative rate (issue #1989).
        assertEquals(0L, SkillSimulator.estimateAgilityXp(10, 50, 1))
    }

    @Test
    fun `estimateAgilityXp is monotonically non-decreasing in current level`() {
        var previous = Long.MIN_VALUE
        for (level in 1..99) {
            val xp = SkillSimulator.estimateAgilityXp(10, 1, level)
            assertTrue("XP dropped at level $level", xp >= previous)
            previous = xp
        }
    }

    @Test
    fun `sessionDurationMs scales linearly from 60 to 40 minutes`() {
        // Millisecond precision (issue #1486): mid-scale levels land between whole minutes.
        assertEquals(3_600_000L, SkillSimulator.sessionDurationMs(1))
        assertEquals(3_306_122L, SkillSimulator.sessionDurationMs(25))
        assertEquals(3_000_000L, SkillSimulator.sessionDurationMs(50))
        assertEquals(2_693_878L, SkillSimulator.sessionDurationMs(75))
        assertEquals(2_400_000L, SkillSimulator.sessionDurationMs(99))
    }

    @Test
    fun `sessionDurationMs clamps out-of-range levels and never increases with level`() {
        assertEquals(SkillSimulator.sessionDurationMs(1), SkillSimulator.sessionDurationMs(0))
        assertEquals(SkillSimulator.sessionDurationMs(99), SkillSimulator.sessionDurationMs(200))
        var previous = Long.MAX_VALUE
        for (level in 1..99) {
            val ms = SkillSimulator.sessionDurationMs(level)
            assertTrue("duration increased at level $level", ms <= previous)
            previous = ms
        }
    }

    @Test
    fun `sessionDurationMs applies chronosMultiplier correctly`() {
        // Base 60 minutes = 3,600,000 ms
        val baseMs = SkillSimulator.sessionDurationMs(1, 0.0, 1.0f)
        assertEquals(3_600_000L, baseMs)

        // Chronos Spire Tier 1 (-2% reduction -> 0.98 multiplier)
        // 60 min * 0.98 = 58.8 min = 3,528,000 ms (no whole-minute rounding, issue #1486)
        val tier1Ms = SkillSimulator.sessionDurationMs(1, 0.0, 0.98f)
        assertEquals(3_528_000L, tier1Ms)

        // Chronos Spire Tier 3 (-6% reduction -> 0.94 multiplier)
        // 60 min * 0.94 = 56.4 min = 3,384,000 ms
        val tier3Ms = SkillSimulator.sessionDurationMs(1, 0.0, 0.94f)
        assertEquals(3_384_000L, tier3Ms)
    }
}
