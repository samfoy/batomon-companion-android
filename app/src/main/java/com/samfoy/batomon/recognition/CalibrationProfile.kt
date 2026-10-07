package com.samfoy.batomon.recognition

import android.content.Context

data class CalibrationProfile(val board: NormalizedRect = ThorRegions.board) {
    fun save(context: Context) { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putFloat("left", board.left).putFloat("top", board.top).putFloat("width", board.width).putFloat("height", board.height).apply() }
    companion object {
        private const val PREFERENCES = "recognition_calibration"
        fun load(context: Context): CalibrationProfile { val p = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE); return CalibrationProfile(NormalizedRect(p.getFloat("left", ThorRegions.board.left), p.getFloat("top", ThorRegions.board.top), p.getFloat("width", ThorRegions.board.width), p.getFloat("height", ThorRegions.board.height))) }
    }
}
