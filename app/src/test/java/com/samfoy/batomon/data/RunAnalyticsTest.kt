package com.samfoy.batomon.data

import org.junit.Assert.*
import org.junit.Test

class RunAnalyticsTest {
    @Test fun summarizesCompletedRunsAndRounds() {
        val runs = listOf(
            RunEntity(startedAt = 1, result = "Win", board = "A · B", roundResults = "W · L · W"),
            RunEntity(startedAt = 2, result = "Loss", board = "A · B", roundResults = "L · L"),
            RunEntity(startedAt = 3, result = "In progress", board = "A · B", roundResults = "W")
        )
        val stats = RunAnalytics.summarize(runs)
        assertEquals(2, stats.completed); assertEquals(1, stats.wins); assertEquals(5, stats.rounds); assertEquals(2, stats.roundWins); assertEquals("A · B", stats.mostRepeatedBoard); assertEquals(2, stats.mostRepeatedCount)
    }
}
