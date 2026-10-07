package com.samfoy.batomon

import android.app.*
import android.content.*
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.samfoy.batomon.capture.CaptureService
import com.samfoy.batomon.data.BatomonDatabase
import com.samfoy.batomon.data.RunEntity
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val tabButtons = linkedMapOf<String, TextView>()
    private val io = Executors.newSingleThreadExecutor()
    private val database by lazy { BatomonDatabase.get(this) }
    private var captureActive = false
    private var receiverRegistered = false
    private var currentRun: RunEntity? = null
    private var roundLabel: TextView? = null
    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java).apply {
            putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode); putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
        }) else updateStatus("Capture permission cancelled", R.color.muted)
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_SAVE_FRAME; putExtra(CaptureService.EXTRA_EXPORT_URI, uri); addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        })
    }
    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(CaptureService.EXTRA_STATE)) {
                CaptureService.STATE_CAPTURING -> { captureActive = true; updateStatus("● CAPTURING DEFAULT DISPLAY", R.color.green) }
                CaptureService.STATE_EXPORT_ARMED -> updateStatus("● EXPORT ARMED · waiting for frame", R.color.gold)
                CaptureService.STATE_EXPORT_SAVED -> updateStatus("● DIAGNOSTIC FRAME SAVED", R.color.green)
                CaptureService.STATE_ERROR -> { captureActive = false; updateStatus("Capture error: ${intent.getStringExtra(CaptureService.EXTRA_ERROR) ?: "unknown"}", R.color.pink) }
                CaptureService.STATE_IDLE -> { captureActive = false; updateStatus("○ IDLE · ready", R.color.muted) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !intent.getBooleanExtra("routed", false)) routeToSmallestSecondaryDisplay()
        buildUi()
    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(CaptureService.ACTION_CAPTURE_STATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(captureReceiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(captureReceiver, filter)
        receiverRegistered = true
    }
    override fun onStop() { if (receiverRegistered) { unregisterReceiver(captureReceiver); receiverRegistered = false }; super.onStop() }
    override fun onDestroy() { io.shutdownNow(); super.onDestroy() }

    private fun routeToSmallestSecondaryDisplay() {
        val secondary = getSystemService(DisplayManager::class.java).displays.filter { it.displayId != Display.DEFAULT_DISPLAY }.minByOrNull { it.mode.physicalWidth * it.mode.physicalHeight } ?: return
        runCatching { startActivity(Intent(this, MainActivity::class.java).putExtra("routed", true), ActivityOptions.makeBasic().apply { setLaunchDisplayId(secondary.displayId) }.toBundle()) }.onSuccess { finish() }
    }

    private fun buildUi() {
        window.statusBarColor = color(R.color.night); window.navigationBarColor = color(R.color.night)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(color(R.color.night)); setPadding(dp(16), dp(12), dp(16), dp(10)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "BATOMON"; setTextColor(color(R.color.cyan)); textSize = 22f; typeface = android.graphics.Typeface.DEFAULT_BOLD })
        header.addView(TextView(this).apply { text = "  COMPANION"; setTextColor(color(R.color.text)); textSize = 16f }, LinearLayout.LayoutParams(0, -2, 1f))
        status = TextView(this).apply { text = "○ IDLE · ready"; setTextColor(color(R.color.muted)); textSize = 12f; gravity = Gravity.CENTER_VERTICAL; maxLines = 2 }
        header.addView(status, LinearLayout.LayoutParams(dp(190), -2)); root.addView(header, LinearLayout.LayoutParams(-1, dp(42)))
        val nav = LinearLayout(this).apply { setPadding(0, dp(8), 0, dp(8)); gravity = Gravity.CENTER_VERTICAL }
        listOf("LIVE", "RUNS", "COMPS", "DEX", "SETTINGS").forEach { label -> val button = TextView(this).apply { text = label; textSize = 12f; gravity = Gravity.CENTER; setPadding(dp(7), dp(10), dp(7), dp(10)); setOnClickListener { showTab(label) } }; tabButtons[label] = button; nav.addView(button, LinearLayout.LayoutParams(0, -2, 1f)) }
        root.addView(nav); content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(8)) }; root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root); showTab("LIVE")
    }

    private fun showTab(label: String) { tabButtons.forEach { (key, view) -> view.setTextColor(color(if (key == label) R.color.cyan else R.color.muted)) }; content.removeAllViews(); roundLabel = null; when (label) { "LIVE" -> liveTab(); "RUNS" -> runsTab(); "COMPS" -> compsTab(); "DEX" -> dexTab(); "SETTINGS" -> settingsTab() } }

    private fun liveTab() {
        val card = card(); card.addView(label("LIVE SESSION", R.color.muted)); card.addView(title(if (currentRun == null) "Track a run manually" else "Run in progress")); card.addView(body("Manual tracking is always available. Automatic capture remains diagnostic until real Thor fixtures calibrate the recognizer."))
        val mode = field("Mode", currentRun?.mode ?: "Ranked"); val board = field("Opening board / notes", currentRun?.board ?: ""); card.addView(mode); card.addView(board)
        card.addView(button(if (currentRun == null) "BEGIN MANUAL RUN" else "RUN STARTED", R.color.cyan) { if (currentRun == null) beginRun(mode.text.toString(), board.text.toString()) else toast("This run is already in progress") }, LinearLayout.LayoutParams(-1, dp(46)))
        roundLabel = TextView(this).apply { text = roundSummary(); setTextColor(color(R.color.text)); textSize = 14f; setPadding(0, dp(12), 0, dp(8)) }; card.addView(roundLabel)
        val rounds = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; rounds.addView(button("ROUND WIN", R.color.green) { recordRound(true) }, LinearLayout.LayoutParams(0, dp(42), 1f)); rounds.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1)); rounds.addView(button("ROUND LOSS", R.color.pink) { recordRound(false) }, LinearLayout.LayoutParams(0, dp(42), 1f)); card.addView(rounds)
        val finishes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }; finishes.addView(button("FINISH WIN", R.color.gold) { finishRun("Win") }, LinearLayout.LayoutParams(0, dp(42), 1f)); finishes.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1)); finishes.addView(button("FINISH LOSS", R.color.surface_alt) { finishRun("Loss") }, LinearLayout.LayoutParams(0, dp(42), 1f)); card.addView(finishes); content.addView(card)
        val capture = card(); capture.addView(label("AUTOMATIC CAPTURE", R.color.muted)); capture.addView(body("Keep Batomon on the upper/default display. The Android system prompt appears once per session; raw frames are processed in memory.")); capture.addView(button("START SCREEN CAPTURE", R.color.cyan) { projectionLauncher.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()) }, LinearLayout.LayoutParams(-1, dp(46))); val stop = button("STOP CAPTURE", R.color.pink) { stopService(Intent(this, CaptureService::class.java)) }; val stopParams = LinearLayout.LayoutParams(-1, dp(40)); stopParams.topMargin = dp(8); capture.addView(stop, stopParams); content.addView(capture); content.addView(cardWith("CURRENT STATE", "Scene: UNKNOWN\nConfidence: —\nRound: ${currentRun?.rounds ?: "—"}"))
    }

    private fun runsTab() { content.addView(cardWith("RUN HISTORY", "Manual runs persist locally in Room. Tap a run for its round-by-round recap.")); val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; content.addView(list); io.execute { val runs = database.runs().getRuns(); runOnUiThread { list.removeAllViews(); if (runs.isEmpty()) list.addView(cardWith("NO RUNS YET", "Start a manual run from Live.")); runs.forEach { run -> val row = card(); row.addView(label(formatDate(run.startedAt) + " · " + run.mode.uppercase(Locale.US), R.color.cyan)); row.addView(title("${run.result} · ${run.rounds} rounds")); row.addView(body("Opening: ${run.board.ifBlank { "not entered" }}\nRounds: ${run.roundResults.ifBlank { "—" }}\nLives: ${run.lives}")); row.setOnClickListener { showRecap(run) }; list.addView(row) } } } }
    private fun showRecap(run: RunEntity) { AlertDialog.Builder(this).setTitle("Run recap").setMessage("${formatDate(run.startedAt)} · ${run.mode}\nResult: ${run.result}\nRounds: ${run.rounds}\nRound results: ${run.roundResults.ifBlank { "—" }}\nOpening board: ${run.board.ifBlank { "not entered" }}\nLives remaining: ${run.lives}\n\n${run.notes}").setPositiveButton("Close", null).show() }
    private fun compsTab() { content.addView(cardWith("COMPS", "No comp recommendations are bundled. Use the public community comp reference in a browser; this app does not copy third-party artwork or claim current meta accuracy.")); content.addView(cardWith("SOURCE", "https://batomon.com/comps\nPublic rankings are dated observations, not predictions.")) }

    private fun dexTab() {
        val card = card(); card.addView(label("DEX · ITEMS", R.color.muted)); card.addView(body("Text-only Balance 24 / game build 25600878 catalog. Artwork is not bundled while licensing is pending.")); val search = field("Search item or effect", ""); card.addView(search); val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; card.addView(results); content.addView(card)
        val items = loadItems(); fun render() { results.removeAllViews(); val q = search.text.toString().trim().lowercase(Locale.US); items.filter { q.isBlank() || it.first.lowercase(Locale.US).contains(q) || it.second.lowercase(Locale.US).contains(q) }.take(40).forEach { item -> results.addView(TextView(this).apply { text = "${item.first}\n${item.second}"; textSize = 13f; setTextColor(color(R.color.text)); setPadding(0, dp(8), 0, dp(8)) }) } }; search.addTextChangedListener(SimpleTextWatcher { render() }); render()
    }

    private fun settingsTab() { content.addView(cardWith("THOR DISPLAY", displayDescription())); val diagnostics = card(); diagnostics.addView(label("DIAGNOSTICS", R.color.muted)); diagnostics.addView(body("Saving is never automatic. Choose a destination, then the service saves one explicitly requested frame with system strips redacted and a visible marker.")); diagnostics.addView(button("SAVE REDACTED CURRENT FRAME", R.color.gold) { if (captureActive) exportLauncher.launch("batomon-diagnostic-${System.currentTimeMillis()}.png") else toast("Start screen capture first") }); content.addView(diagnostics); content.addView(cardWith("REFERENCE", "Balance 24 / game build 25600878\nSource: https://batomon.com/items\nText-only metadata is bundled; no image assets are redistributed.")); content.addView(cardWith("VERSION", "0.2.0 · GPLv3")) }

    private fun beginRun(mode: String, board: String) { io.execute { val start = System.currentTimeMillis(); val id = database.runs().insertBlocking(RunEntity(startedAt = start, mode = mode.ifBlank { "Unknown" }, board = board, source = "manual")); currentRun = RunEntity(id = id, startedAt = start, mode = mode.ifBlank { "Unknown" }, board = board, source = "manual"); runOnUiThread { toast("Run started"); roundLabel?.text = roundSummary() } } }
    private fun recordRound(win: Boolean) { val run = currentRun ?: return toast("Begin a manual run first"); val results = run.roundResults + if (run.roundResults.isBlank()) if (win) "W" else "L" else " · " + if (win) "W" else "L"; val next = run.copy(rounds = run.rounds + 1, lives = if (win) run.lives else (run.lives - 1).coerceAtLeast(0), roundResults = results); currentRun = next; io.execute { database.runs().updateBlocking(next) }; roundLabel?.text = roundSummary() }
    private fun finishRun(result: String) { val run = currentRun ?: return toast("Begin a manual run first"); val next = run.copy(endedAt = System.currentTimeMillis(), result = result); io.execute { database.runs().updateBlocking(next) }; currentRun = null; toast("Run saved"); roundLabel?.text = "No active run" }
    private fun roundSummary() = currentRun?.let { "Round ${it.rounds} · ${it.roundResults.ifBlank { "no results yet" }} · ${it.lives} lives" } ?: "No active run"
    private fun loadItems(): List<Pair<String, String>> = runCatching { val text = assets.open("reference/items_balance24.json").bufferedReader().use { it.readText() }; val array = JSONArray(org.json.JSONObject(text).getJSONArray("items").toString()); (0 until array.length()).map { val obj = array.getJSONObject(it); obj.getString("name") to obj.getString("effect") } }.getOrDefault(emptyList())
    private fun displayDescription(): String = getSystemService(DisplayManager::class.java).displays.joinToString("\n") { "Display ${it.displayId}: ${it.mode.physicalWidth}×${it.mode.physicalHeight}${if (it.displayId == Display.DEFAULT_DISPLAY) " · default (capture source)" else " · secondary (Companion target)"}" }
    private fun cardWith(heading: String, text: String): View = card().also { it.addView(label(heading, R.color.muted)); it.addView(body(text)) }
    private fun card(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundColor(color(R.color.surface)); val p = LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(10)); layoutParams = p }
    private fun field(hint: String, value: String): EditText = EditText(this).apply { setHint(hint); setText(value); setTextColor(color(R.color.text)); setHintTextColor(color(R.color.muted)); textSize = 14f; isSingleLine = true; setPadding(0, dp(7), 0, dp(7)) }
    private fun title(s: String) = TextView(this).apply { text = s; textSize = 21f; setTextColor(color(R.color.text)); setPadding(0, dp(6), 0, dp(4)) }
    private fun label(s: String, c: Int) = TextView(this).apply { text = s; textSize = 11f; setTextColor(color(c)); letterSpacing = .12f }
    private fun body(s: String) = TextView(this).apply { text = s; textSize = 14f; setTextColor(color(R.color.muted)); setPadding(0, dp(8), 0, dp(12)) }
    private fun button(s: String, c: Int, click: () -> Unit) = TextView(this).apply { text = s; gravity = Gravity.CENTER; textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(color(R.color.night)); setBackgroundColor(color(c)); setOnClickListener { click() } }
    private fun updateStatus(value: String, c: Int) { if (::status.isInitialized) { status.text = value; status.setTextColor(color(c)) } }
    private fun formatDate(time: Long) = SimpleDateFormat("MMM d · HH:mm", Locale.US).format(Date(time))
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

private class SimpleTextWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
