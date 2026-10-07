package com.samfoy.batomon

import android.app.*
import android.content.*
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.samfoy.batomon.capture.CaptureService
import com.samfoy.batomon.data.BatomonDatabase
import com.samfoy.batomon.data.RunEntity
import com.samfoy.batomon.data.RunAnalytics
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
    private var boardSpinners: List<Spinner> = emptyList()
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
    private val historyExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) io.execute { val json = database.runs().getRuns().joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { run -> "  {\"startedAt\":${run.startedAt},\"mode\":${jsonString(run.mode)},\"result\":${jsonString(run.result)},\"rounds\":${run.rounds},\"roundResults\":${jsonString(run.roundResults)},\"board\":${jsonString(run.board)},\"lives\":${run.lives}}" }; contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }; runOnUiThread { toast("Run history exported") } }
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
        val mode = field("Mode", currentRun?.mode ?: "Ranked"); card.addView(mode)
        card.addView(label("OPENING BOARD · 6 SLOTS", R.color.muted))
        val names = listOf("—") + loadReference("batomon").map { it.name }
        val boardRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        boardSpinners = (0 until 6).map { index -> Spinner(this).also { spinner -> spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names); val selected = currentRun?.board?.split(" · ")?.getOrNull(index); val position = names.indexOf(selected).coerceAtLeast(0); spinner.setSelection(position); boardRow.addView(spinner, LinearLayout.LayoutParams(0, dp(52), 1f)) } }
        card.addView(boardRow)
        val notes = field("Optional run notes", ""); card.addView(notes)
        card.addView(button(if (currentRun == null) "BEGIN MANUAL RUN" else "RUN STARTED", R.color.cyan) { if (currentRun == null) beginRun(mode.text.toString(), boardSpinners.map { it.selectedItem?.toString().orEmpty() }.filter { it != "—" }.joinToString(" · "), notes.text.toString()) else toast("This run is already in progress") }, LinearLayout.LayoutParams(-1, dp(46)))
        roundLabel = TextView(this).apply { text = roundSummary(); setTextColor(color(R.color.text)); textSize = 14f; setPadding(0, dp(12), 0, dp(8)) }; card.addView(roundLabel)
        val rounds = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; rounds.addView(button("ROUND WIN", R.color.green) { recordRound(true) }, LinearLayout.LayoutParams(0, dp(42), 1f)); rounds.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1)); rounds.addView(button("ROUND LOSS", R.color.pink) { recordRound(false) }, LinearLayout.LayoutParams(0, dp(42), 1f)); card.addView(rounds)
        val finishes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }; finishes.addView(button("FINISH WIN", R.color.gold) { finishRun("Win") }, LinearLayout.LayoutParams(0, dp(42), 1f)); finishes.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1)); finishes.addView(button("FINISH LOSS", R.color.surface_alt) { finishRun("Loss") }, LinearLayout.LayoutParams(0, dp(42), 1f)); card.addView(finishes); content.addView(card)
        val capture = card(); capture.addView(label("AUTOMATIC CAPTURE", R.color.muted)); capture.addView(body("Keep Batomon on the upper/default display. The Android system prompt appears once per session; raw frames are processed in memory.")); capture.addView(button("START SCREEN CAPTURE", R.color.cyan) { projectionLauncher.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()) }, LinearLayout.LayoutParams(-1, dp(46))); val stop = button("STOP CAPTURE", R.color.pink) { stopService(Intent(this, CaptureService::class.java)) }; val stopParams = LinearLayout.LayoutParams(-1, dp(40)); stopParams.topMargin = dp(8); capture.addView(stop, stopParams); content.addView(capture); content.addView(cardWith("CURRENT STATE", "Scene: UNKNOWN\nConfidence: —\nRound: ${currentRun?.rounds ?: "—"}"))
    }

    private fun runsTab() {
        content.addView(cardWith("RUN HISTORY", "Manual runs persist locally in Room. Filter results, compare opening boards, or tap a run for its recap."))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val filter = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("All results", "Win", "Loss", "In progress")) }
        val boardSearch = field("Filter board", ""); controls.addView(filter, LinearLayout.LayoutParams(0, dp(52), 1f)); controls.addView(boardSearch, LinearLayout.LayoutParams(0, dp(52), 1f)); content.addView(controls)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; actions.addView(button("EXPORT JSON", R.color.gold) { historyExportLauncher.launch("batomon-runs-${System.currentTimeMillis()}.json") }, LinearLayout.LayoutParams(0, dp(42), 1f)); content.addView(actions)
        val analytics = TextView(this).apply { setTextColor(color(R.color.text)); textSize = 13f; setPadding(dp(8), dp(12), dp(8), dp(12)) }; content.addView(analytics)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; content.addView(list)
        io.execute { val runs = database.runs().getRuns(); runOnUiThread {
            fun render() { val wanted = filter.selectedItem?.toString(); val q = boardSearch.text.toString().trim().lowercase(Locale.US); val filtered = runs.filter { (wanted == "All results" || it.result == wanted) && (q.isBlank() || it.board.lowercase(Locale.US).contains(q)) }; list.removeAllViews(); analytics.text = analyticsSummary(filtered); if (filtered.isEmpty()) list.addView(cardWith("NO MATCHES", "Change the filter or start a run from Live.")); filtered.forEach { run -> val row = card(); row.addView(label(formatDate(run.startedAt) + " · " + run.mode.uppercase(Locale.US), R.color.cyan)); row.addView(title("${run.result} · ${run.rounds} rounds")); row.addView(body("Opening: ${run.board.ifBlank { "not entered" }}\nRounds: ${run.roundResults.ifBlank { "—" }}\nLives: ${run.lives}")); row.setOnClickListener { showRecap(run) }; list.addView(row) } }
            filter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit; override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = render() }; boardSearch.addTextChangedListener(SimpleTextWatcher { render() }); render()
        } }
    }
    private fun analyticsSummary(runs: List<RunEntity>): String { val stats = RunAnalytics.summarize(runs); return "${stats.completed} completed · ${stats.wins} wins · ${stats.rounds} rounds · round win rate ${if (stats.rounds == 0) "—" else "${stats.roundWins * 100 / stats.rounds}%"}\nMost repeated opening: ${stats.mostRepeatedBoard ?: "—"} (${stats.mostRepeatedCount} runs)" }
    private fun showRecap(run: RunEntity) { AlertDialog.Builder(this).setTitle("Run recap").setMessage("${formatDate(run.startedAt)} · ${run.mode}\nResult: ${run.result}\nRounds: ${run.rounds}\nRound results: ${run.roundResults.ifBlank { "—" }}\nOpening board: ${run.board.ifBlank { "not entered" }}\nLives remaining: ${run.lives}\n\n${run.notes}").setNeutralButton("Delete") { _, _ -> confirmDelete(run) }.setPositiveButton("Close", null).show() }
    private fun confirmDelete(run: RunEntity) { AlertDialog.Builder(this).setTitle("Delete this run?").setMessage("This removes the local record and cannot be undone.").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> io.execute { database.runs().deleteBlocking(run); runOnUiThread { showTab("RUNS") } } }.show() }
    private fun compsTab() {
        val card = card(); card.addView(label("COMPS · CURRENT PUBLIC REFERENCE", R.color.muted)); card.addView(body("Comp rankings change with the game balance. The offline app keeps your run history; these buttons open the current public pages without adding network permission.")); card.addView(button("OPEN PUBLIC TIER LIST", R.color.cyan) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://batomon.com/comps"))) }, LinearLayout.LayoutParams(-1, dp(44))); val team = button("OPEN TEAM BUILDER", R.color.gold) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://batomon.com/lab"))) }; val p = LinearLayout.LayoutParams(-1, dp(44)); p.topMargin = dp(8); card.addView(team, p); content.addView(card)
        content.addView(cardWith("READING THE DATA", "Public rankings are dated observations, not predictions. The app does not download or cache comp artwork."))
    }

    private fun dexTab() {
        val card = card(); card.addView(label("DEX · OFFLINE REFERENCE", R.color.muted)); card.addView(body("Text-only Balance 24 / game build 25600878 catalogs. Search names and effects; artwork is not bundled while licensing is pending.")); val category = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Batomon", "Trinkets", "Trainers", "Items")) }; card.addView(category); val search = field("Search name or effect", ""); card.addView(search); val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; card.addView(results); content.addView(card)
        var entries = loadReference("batomon"); fun render() { results.removeAllViews(); val q = search.text.toString().trim().lowercase(Locale.US); entries.filter { q.isBlank() || it.name.lowercase(Locale.US).contains(q) || it.description.lowercase(Locale.US).contains(q) }.take(60).forEach { item -> results.addView(TextView(this).apply { text = "${item.name}\n${item.description}"; textSize = 13f; setTextColor(color(R.color.text)); setPadding(0, dp(8), 0, dp(8)); setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle(item.name).setMessage("${item.description}\n\nSource: ${item.source}").setPositiveButton("Close", null).show() } }) } }; category.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit; override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { entries = loadReference(listOf("batomon", "trinket", "trainer", "item")[position]); render() } }; search.addTextChangedListener(SimpleTextWatcher { render() }); render()
    }

    private fun settingsTab() { content.addView(cardWith("THOR DISPLAY", displayDescription())); val diagnostics = card(); diagnostics.addView(label("DIAGNOSTICS", R.color.muted)); diagnostics.addView(body("Saving is never automatic. Choose a destination, then the service saves one explicitly requested frame with system strips redacted and a visible marker.")); diagnostics.addView(button("SAVE REDACTED CURRENT FRAME", R.color.gold) { if (captureActive) exportLauncher.launch("batomon-diagnostic-${System.currentTimeMillis()}.png") else toast("Start screen capture first") }); content.addView(diagnostics); content.addView(cardWith("REFERENCE", "Balance 24 / game build 25600878\nOffline catalogs: Batomon, trinkets, trainers, items\nNo image assets are redistributed.")); content.addView(cardWith("VERSION", "0.3.0 · GPLv3")) }

    private fun beginRun(mode: String, board: String, notes: String) { io.execute { val start = System.currentTimeMillis(); val normalizedMode = mode.ifBlank { "Unknown" }; val id = database.runs().insertBlocking(RunEntity(startedAt = start, mode = normalizedMode, board = board, notes = notes, source = "manual")); currentRun = RunEntity(id = id, startedAt = start, mode = normalizedMode, board = board, notes = notes, source = "manual"); runOnUiThread { toast("Run started"); roundLabel?.text = roundSummary() } } }
    private fun recordRound(win: Boolean) { val run = currentRun ?: return toast("Begin a manual run first"); val results = run.roundResults + if (run.roundResults.isBlank()) if (win) "W" else "L" else " · " + if (win) "W" else "L"; val next = run.copy(rounds = run.rounds + 1, lives = if (win) run.lives else (run.lives - 1).coerceAtLeast(0), roundResults = results); currentRun = next; io.execute { database.runs().updateBlocking(next) }; roundLabel?.text = roundSummary() }
    private fun finishRun(result: String) { val run = currentRun ?: return toast("Begin a manual run first"); val next = run.copy(endedAt = System.currentTimeMillis(), result = result); io.execute { database.runs().updateBlocking(next) }; currentRun = null; toast("Run saved"); roundLabel?.text = "No active run" }
    private fun roundSummary() = currentRun?.let { "Round ${it.rounds} · ${it.roundResults.ifBlank { "no results yet" }} · ${it.lives} lives" } ?: "No active run"
    private fun loadReference(kind: String): List<ReferenceEntry> = runCatching { val file = if (kind == "item") "items_balance24.json" else "${kind}s_balance24.json"; val root = org.json.JSONObject(assets.open("reference/$file").bufferedReader().use { it.readText() }); val array = root.optJSONArray("entries") ?: root.optJSONArray("items") ?: JSONArray(); (0 until array.length()).map { val obj = array.getJSONObject(it); ReferenceEntry(obj.getString("name"), obj.optString("description", obj.optString("effect")), root.optString("source")) } }.getOrDefault(emptyList())
    private fun jsonString(value: String): String = org.json.JSONObject.quote(value)
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

private data class ReferenceEntry(val name: String, val description: String, val source: String)

private class SimpleTextWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
