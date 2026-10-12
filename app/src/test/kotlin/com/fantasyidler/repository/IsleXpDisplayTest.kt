package com.fantasyidler.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Elder Isle payouts multiply raw frame XP by elder level scaling (+5% per level) and the
 * embedded Sapphire stack (+5% each). Previews, the session card and the Collect summary
 * share [isleXpMult] / [isleFramesXp] with the payout, so the shown XP is what is granted.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class IsleXpDisplayTest {

    private lateinit var db: AppDatabase
    private lateinit var playerRepo: PlayerRepository

    private val fourSapphires = mapOf(
        "elder_helm"      to "elder_sapphire",
        "elder_platebody" to "elder_sapphire",
        "elder_platelegs" to "elder_sapphire",
        "elder_boots"     to "elder_sapphire",
        "elder_cape"      to "elder_ruby",
    )

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val gameData = GameDataRepository(context, json)
        playerRepo = PlayerRepository(
            db.playerDao(),
            db.questProgressDao(),
            db.farmingPatchDao(),
            json,
            DailyQuestRepository(gameData),
            WeeklyQuestRepository(gameData),
            BuffNotificationScheduler(context),
            gameData,
            BoostRepository(gameData),
            db,
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun frame(xp: Int, bySkill: Map<String, Long> = emptyMap()) = SessionFrame(
        minute = 1, xpGain = xp, xpBefore = 0L, xpAfter = 0L, levelBefore = 1, levelAfter = 1,
        xpBySkill = bySkill,
    )

    @Test
    fun `isle multiplier combines elder level scaling and sapphires`() {
        assertEquals(1.0, isleXpMult(1, emptyMap()), 1e-9)
        assertEquals(3.95, isleXpMult(60, emptyMap()), 1e-9)
        // Rubies do not count toward XP; four sapphires add 20%.
        assertEquals(3.95 * 1.20, isleXpMult(60, fourSapphires), 1e-9)
    }

    @Test
    fun `combat frames scale each skill by its own elder level`() {
        val frames = listOf(
            frame(150, mapOf(Skills.ATTACK to 100L, Skills.HITPOINTS to 50L)),
            frame(150, mapOf(Skills.ATTACK to 100L, Skills.HITPOINTS to 50L)),
        )
        val levels = mapOf(Skills.ATTACK to 21, Skills.HITPOINTS to 1)
        // Attack 200 * 2.0 = 400, Hitpoints 100 * 1.0 = 100.
        assertEquals(500L, isleFramesXp(frames, "combat", levels, emptyMap()))
    }

    @Test
    fun `skill session preview matches the isle payout`() = runBlocking {
        playerRepo.getOrCreatePlayer()
        playerRepo.updateFlags(playerRepo.getFlags().copy(
            elderSkillLevels = mapOf(Skills.MINING to 30),
            embeddedSigils   = fourSapphires,
        ))
        val frames = List(60) { frame(37) }
        val flags = playerRepo.getFlags()
        val preview = isleFramesXp(frames, Skills.MINING, flags.elderSkillLevels, flags.embeddedSigils)
        val granted = playerRepo.applyElderSessionResults(Skills.MINING, frames.sumOf { it.xpGain.toLong() }, emptyMap())
        assertEquals(granted, preview)
        assertEquals(granted, playerRepo.getFlags().elderSkillXp[Skills.MINING])
    }

    @Test
    fun `combat preview matches the isle multi-skill payout`() = runBlocking {
        playerRepo.getOrCreatePlayer()
        playerRepo.updateFlags(playerRepo.getFlags().copy(
            elderSkillLevels = mapOf(Skills.STRENGTH to 45, Skills.DEFENSE to 12),
            embeddedSigils   = fourSapphires,
        ))
        val frames = List(60) { frame(19, mapOf(Skills.STRENGTH to 13L, Skills.DEFENSE to 6L)) }
        val flags = playerRepo.getFlags()
        val preview = isleFramesXp(frames, "combat", flags.elderSkillLevels, flags.embeddedSigils)
        val perSkill = mapOf(Skills.STRENGTH to 60L * 13, Skills.DEFENSE to 60L * 6)
        val granted = playerRepo.applyElderMultiSkillResults(perSkill, emptyMap(), 0L)
        assertEquals(granted.values.sum(), preview)
    }
}
