package com.samfoy.batomon

import android.app.*
import android.content.*
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.samfoy.batomon.capture.CaptureService

class MainActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
            })
            status.text = "● CAPTURING DEFAULT DISPLAY"
            status.setTextColor(color(R.color.green))
        } else { status.text = "Capture permission cancelled" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !intent.getBooleanExtra("routed", false)) routeToSmallestSecondaryDisplay()
        buildUi()
    }

    private fun routeToSmallestSecondaryDisplay() {
        val dm = getSystemService(DisplayManager::class.java)
        val secondary = dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }
            .minByOrNull { it.mode.physicalWidth * it.mode.physicalHeight } ?: return
        runCatching {
            val options = ActivityOptions.makeBasic().apply { setLaunchDisplayId(secondary.displayId) }
            startActivity(Intent(this, MainActivity::class.java).putExtra("routed", true), options.toBundle())
        }.onSuccess { finish() }
    }

    private fun buildUi() {
        window.statusBarColor = color(R.color.night); window.navigationBarColor = color(R.color.night)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(color(R.color.night)); setPadding(dp(16), dp(12), dp(16), dp(10)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "BATOMON"; setTextColor(color(R.color.cyan)); textSize = 22f; typeface = android.graphics.Typeface.DEFAULT_BOLD })
        header.addView(TextView(this).apply { text = "  COMPANION"; setTextColor(color(R.color.text)); textSize = 16f }, LinearLayout.LayoutParams(0, -2, 1f))
        status = TextView(this).apply { text = "○ IDLE · diagnostic build"; setTextColor(color(R.color.muted)); textSize = 12f; gravity = Gravity.CENTER_VERTICAL }
        header.addView(status)
        root.addView(header, LinearLayout.LayoutParams(-1, dp(38)))

        val nav = LinearLayout(this).apply { setPadding(0, dp(10), 0, dp(8)); gravity = Gravity.CENTER_VERTICAL }
        listOf("LIVE", "RUNS", "COMPS", "DEX", "SETTINGS").forEachIndexed { index, label ->
            val button = TextView(this).apply { text = label; textSize = 12f; gravity = Gravity.CENTER; setTextColor(if (index == 0) color(R.color.cyan) else color(R.color.muted)); setPadding(dp(12), dp(9), dp(12), dp(9)); setOnClickListener { showTab(label) } }
            nav.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, 0) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        showTab("LIVE")
    }

    private fun showTab(label: String) {
        content.removeAllViews()
        when (label) {
            "LIVE" -> liveTab(); "RUNS" -> runsTab(); "COMPS" -> placeholderTab("COMPS", "Saved compositions will appear here.", "No comp data has been bundled.")
            "DEX" -> placeholderTab("DEX", "Batomon reference", "Search and reference cards will be connected after fixture data is available.")
            else -> settingsTab()
        }
    }

    private fun liveTab() {
        val card = card()
        card.addView(label("SESSION", color(R.color.muted)))
        card.addView(title("Ready to track a run"))
        card.addView(body("Keep Batomon on the upper/default display. Android will ask for one-time-per-session screen capture permission; frames are processed in memory and never uploaded."))
        val start = button("START CAPTURE", color(R.color.cyan)) { projectionLauncher.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()) }
        card.addView(start, LinearLayout.LayoutParams(-1, dp(48)))
        val stop = button("STOP CAPTURE", color(R.color.pink)) { stopService(Intent(this, CaptureService::class.java)); status.text = "○ IDLE · capture stopped"; status.setTextColor(color(R.color.muted)) }
        val stopParams = LinearLayout.LayoutParams(-1, dp(42)); stopParams.topMargin = dp(8); card.addView(stop, stopParams)
        card.addView(spacer(10))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(metric("SCENE", "UNKNOWN")); row.addView(metric("CONFIDENCE", "—")); row.addView(metric("ROUND", "—"))
        card.addView(row)
        content.addView(card)
        content.addView(cardWith("THOR DISPLAY", "The smallest non-default display is selected automatically when available. This device can be inspected in Settings."))
    }

    private fun runsTab() { content.addView(cardWith("RUN HISTORY", "No completed runs yet. Start a diagnostic capture on Thor, then recognition fixtures can be calibrated from exported crops.")); content.addView(cardWith("PRIVACY", "Only structured observations are stored locally. Raw MediaProjection frames are closed immediately after each sample.")) }
    private fun settingsTab() { content.addView(cardWith("DISPLAY", displayDescription())); content.addView(cardWith("RECOGNITION", "Fixture-driven and intentionally uncalibrated. Add representative Thor captures under diagnostics/ before enabling recognizers.")); content.addView(cardWith("VERSION", "0.1.0 · GPLv3")) }
    private fun placeholderTab(heading: String, titleText: String, detail: String) { content.addView(cardWith(heading, "$titleText\n\n$detail")) }

    private fun displayDescription(): String { val dm = getSystemService(DisplayManager::class.java); return dm.displays.joinToString("\n") { "Display ${it.displayId}: ${it.mode.physicalWidth}×${it.mode.physicalHeight}${if (it.displayId == Display.DEFAULT_DISPLAY) " · default (capture source)" else " · secondary (Companion target)"}" } }
    private fun cardWith(heading: String, text: String): View = card().also { it.addView(label(heading, color(R.color.muted))); it.addView(body(text)) }
    private fun card(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundColor(color(R.color.surface)); val p = LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(10)); layoutParams = p }
    private fun title(s: String) = TextView(this).apply { text = s; textSize = 22f; setTextColor(color(R.color.text)); setPadding(0, dp(6), 0, dp(4)) }
    private fun label(s: String, c: Int) = TextView(this).apply { text = s; textSize = 11f; setTextColor(c); letterSpacing = .12f }
    private fun body(s: String) = TextView(this).apply { text = s; textSize = 14f; setTextColor(color(R.color.muted)); setPadding(0, dp(8), 0, dp(12)) }
    private fun button(s: String, c: Int, click: () -> Unit) = TextView(this).apply { text = s; gravity = Gravity.CENTER; textSize = 13f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(color(R.color.night)); setBackgroundColor(c); setOnClickListener { click() } }
    private fun metric(k: String, v: String) = TextView(this).apply { text = "$k\n$v"; textSize = 12f; setTextColor(color(R.color.text)); setPadding(0, dp(5), dp(18), dp(5)) }
    private fun spacer(h: Int) = Space(this).apply { minimumHeight = dp(h) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(this, id)
}
