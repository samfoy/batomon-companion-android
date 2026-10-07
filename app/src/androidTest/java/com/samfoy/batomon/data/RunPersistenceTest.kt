package com.samfoy.batomon.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunPersistenceTest {
    private lateinit var database: BatomonDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            BatomonDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun insertsAndReadsManualRunAndObservation() = runBlocking {
        val runId = database.runs().insert(RunEntity(startedAt = 123L, mode = "Ranked", board = "Bambudo · Beetbud"))
        database.runs().insertObservation(ObservationEntity(runId = runId, scene = "BATTLE", confidence = .91f, observedAt = 456L))
        val saved = database.runs().getRuns().single()
        assertEquals(runId, saved.id)
        assertEquals("Ranked", saved.mode)
        assertEquals("Bambudo · Beetbud", saved.board)
    }
}
