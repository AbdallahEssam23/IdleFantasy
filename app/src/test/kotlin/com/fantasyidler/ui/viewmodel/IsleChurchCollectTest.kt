package com.fantasyidler.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Collect dialog XP split by provenance (#2057). The isle portion is the amount the isle
 * payout already granted (elder scaling and Sapphires included), so it takes neither the
 * 2x boost nor the Church multiplier; the mainland portion keeps both.
 */
class IsleChurchCollectTest {

    private val church55 = 1.55f

    @Test
    fun `pure isle display shows granted XP with zero church bonus`() {
        val (display, bonus) = collectSkillDisplayXp(totalXp = 1000L, isleXp = 1000L, boostFactor = 1L, churchMult = church55)
        assertEquals(1000L, display)
        assertEquals(0L, bonus)
    }

    @Test
    fun `pure mainland display keeps church bonus`() {
        // 1.55f.toDouble() is 1.54999995..., so 1000 truncates to 1549, same as the old formula.
        val (display, bonus) = collectSkillDisplayXp(totalXp = 1000L, isleXp = 0L, boostFactor = 1L, churchMult = church55)
        assertEquals(1549L, display)
        assertEquals(549L, bonus)
    }

    @Test
    fun `mixed batch splits church only to mainland`() {
        val (display, bonus) = collectSkillDisplayXp(totalXp = 2000L, isleXp = 1000L, boostFactor = 1L, churchMult = church55)
        assertEquals(2549L, display)
        assertEquals(549L, bonus)
    }

    @Test
    fun `isle portion ignores the xp boost`() {
        val (display, bonus) = collectSkillDisplayXp(totalXp = 1000L, isleXp = 1000L, boostFactor = 2L, churchMult = church55)
        assertEquals(1000L, display)
        assertEquals(0L, bonus)
    }

    @Test
    fun `mixed batch applies the xp boost only to mainland`() {
        val (display, bonus) = collectSkillDisplayXp(totalXp = 2000L, isleXp = 1000L, boostFactor = 2L, churchMult = 1.0f)
        assertEquals(3000L, display)
        assertEquals(0L, bonus)
    }

    @Test
    fun `isle clamps above total`() {
        val (display, bonus) = collectSkillDisplayXp(totalXp = 1000L, isleXp = 5000L, boostFactor = 1L, churchMult = church55)
        assertEquals(1000L, display)
        assertEquals(0L, bonus)
    }
}
