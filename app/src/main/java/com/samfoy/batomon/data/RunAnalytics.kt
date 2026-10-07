package com.samfoy.batomon.data

data class RunStats(val completed: Int, val wins: Int, val rounds: Int, val roundWins: Int, val mostRepeatedBoard: String?, val mostRepeatedCount: Int)

object RunAnalytics {
    fun summarize(runs: List<RunEntity>): RunStats {
        val completed = runs.filter { it.result == "Win" || it.result == "Loss" }
        val rounds = completed.flatMap { it.roundResults.split(" · ").filter { result -> result == "W" || result == "L" } }
        val board = completed.groupBy { it.board.ifBlank { "(board not entered)" } }.maxByOrNull { it.value.size }
        return RunStats(completed.size, completed.count { it.result == "Win" }, rounds.size, rounds.count { it == "W" }, board?.key, board?.value?.size ?: 0)
    }
}
