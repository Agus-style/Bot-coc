package com.cocbot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cocbot.service.*
import com.cocbot.state.BotState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLoot: TextView
    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var btnPause: Button
    private lateinit var tabContent: LinearLayout

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            ScreenCaptureService.start(this, result.resultCode, data)
            android.os.Handler(mainLooper).postDelayed({
                BotService.getInstance()?.startBot()
                if (Settings.canDrawOverlays(this)) FloatingWindowService.start(this)
            }, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUI())
        observeBot()
        checkPermissions()
    }

    private fun buildUI(): android.view.View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0d0d1a"))
        }

        // Header
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(16, 20, 16, 20)
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "⚔️ COC Bot"
                textSize = 20f
                setTextColor(Color.parseColor("#FFD700"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "v2.0"
                textSize = 11f
                setTextColor(Color.parseColor("#888888"))
            })
        })

        // Tab bar
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#16213e"))
        }
        val tabs = listOf("Start", "Settings", "Army", "Kalibrasi")
        val tabBtns = tabs.map { name ->
            Button(this).apply {
                text = name
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        }
        tabBtns.forEach { tabBar.addView(it) }
        root.addView(tabBar)

        // Tab content
        tabContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(tabContent)

        // Setup tab switching
        tabBtns[0].setOnClickListener { showStartTab(); highlightTab(tabBtns, 0) }
        tabBtns[1].setOnClickListener { showSettingsTab(); highlightTab(tabBtns, 1) }
        tabBtns[2].setOnClickListener { showArmyTab(); highlightTab(tabBtns, 2) }
        tabBtns[3].setOnClickListener { showCalibrationTab(); highlightTab(tabBtns, 3) }

        // Show start tab by default
        showStartTab()
        highlightTab(tabBtns, 0)

        return root
    }

    private fun highlightTab(btns: List<Button>, idx: Int) {
        btns.forEachIndexed { i, btn ->
            btn.setTextColor(if (i == idx) Color.parseColor("#FF6B35") else Color.parseColor("#888888"))
        }
    }

    private fun showStartTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Permissions status
        ll.addView(sectionHeader("REQUIRED PERMISSIONS"))

        val overlayOk = Settings.canDrawOverlays(this)
        ll.addView(permissionRow("Overlay (Floating Window)",
            overlayOk, "Grant") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        })

        val accOk = AccessibilityBot.instance != null
        ll.addView(permissionRow("Accessibility Service",
            accOk, "Buka Settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        // Status bar
        tvStatus = TextView(this).apply {
            text = "Status: IDLE"
            textSize = 14f
            setTextColor(Color.parseColor("#00FF88"))
            setPadding(0, 16, 0, 4)
        }
        ll.addView(tvStatus)

        tvStats = TextView(this).apply {
            text = "🏆 Match: 0  💛 Gold: 0  💜 Elixir: 0  🖤 DE: 0"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(12, 8, 12, 8)
        }
        ll.addView(tvStats)

        tvLoot = TextView(this).apply {
            text = "🎯 Scan: -"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD700"))
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(12, 4, 12, 8)
        }
        ll.addView(tvLoot)

        // Control buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 8)
        }

        btnRow.addView(Button(this).apply {
            text = "▶ START"
            setBackgroundColor(Color.parseColor("#00AA44"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
            setOnClickListener { startBot() }
        })

        btnPause = Button(this).apply {
            text = "⏸ PAUSE"
            setBackgroundColor(Color.parseColor("#FF8800"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
            setOnClickListener { togglePause() }
        }
        btnRow.addView(btnPause)

        btnRow.addView(Button(this).apply {
            text = "⏹ STOP"
            setBackgroundColor(Color.parseColor("#CC0000"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { stopBot() }
        })
        ll.addView(btnRow)

        // Log
        ll.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@MainActivity).apply {
                text = "📋 LOG"
                textSize = 12f
                setTextColor(Color.parseColor("#FFD700"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(this@MainActivity).apply {
                text = "Clear"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#333355"))
                setTextColor(Color.parseColor("#AAAAAA"))
                layoutParams = LinearLayout.LayoutParams(120, 60)
                setOnClickListener { BotLogger.clear() }
            })
        })

        tvLog = TextView(this).apply {
            text = ""
            textSize = 10f
            setTextColor(Color.parseColor("#00FF88"))
            setBackgroundColor(Color.parseColor("#0d0d1a"))
            setPadding(12, 12, 12, 12)
            typeface = Typeface.MONOSPACE
        }

        scrollLog = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0d0d1a"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 600)
            addView(tvLog)
        }
        ll.addView(scrollLog)

        sv.addView(ll)
        tabContent.addView(sv)
        observeBot()
    }

    private fun showSettingsTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(sectionHeader("1. LOOTING TARGET"))

        val etGold = inputRow(ll, "Min Gold", BotConfig.minGoldTarget.toString())
        val etElixir = inputRow(ll, "Min Elixir", BotConfig.minElixirTarget.toString())
        val etDark = inputRow(ll, "Min Dark Elixir", BotConfig.minDarkElixirTarget.toString())

        // Gold OR Elixir toggle
        val cbAny = CheckBox(this).apply {
            text = "Gold OR Elixir (salah satu cukup)"
            isChecked = BotConfig.useAnyResource
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, c -> BotConfig.useAnyResource = c }
        }
        ll.addView(cbAny)

        ll.addView(sectionHeader("2. ATTACK STRATEGY"))
        val strategies = listOf("All Sides", "Top Bottom", "Left Right")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, strategies)
            setSelection(BotConfig.attackStrategy.ordinal.coerceAtMost(2))
        }
        ll.addView(spinner)

        ll.addView(sectionHeader("3. WALL UPGRADE"))
        val cbWall = CheckBox(this).apply {
            text = "Auto Upgrade Wall"
            isChecked = BotConfig.autoWallUpgrade
            setTextColor(Color.WHITE)
        }
        ll.addView(cbWall)

        ll.addView(sectionHeader("4. AUTO END BATTLE"))
        val cbEnd = CheckBox(this).apply {
            text = "Auto End If Loot Empty"
            isChecked = BotConfig.autoEndBattle
            setTextColor(Color.WHITE)
        }
        ll.addView(cbEnd)

        ll.addView(sectionHeader("5. MISCELLANEOUS"))
        val cbCollect = CheckBox(this).apply {
            text = "Auto Claim Collectors"
            isChecked = BotConfig.autoCollect
            setTextColor(Color.WHITE)
        }
        ll.addView(cbCollect)

        val etDelay = inputRow(ll, "Max Random Delay (detik)", BotConfig.maxRandomDelay.toString())

        ll.addView(Button(this).apply {
            text = "💾 SIMPAN SETTINGS"
            setBackgroundColor(Color.parseColor("#FF6B35"))
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                BotConfig.minGoldTarget = etGold.text.toString().toLongOrNull() ?: 300_000L
                BotConfig.minElixirTarget = etElixir.text.toString().toLongOrNull() ?: 300_000L
                BotConfig.minDarkElixirTarget = etDark.text.toString().toLongOrNull() ?: 0L
                BotConfig.autoWallUpgrade = cbWall.isChecked
                BotConfig.autoEndBattle = cbEnd.isChecked
                BotConfig.autoCollect = cbCollect.isChecked
                BotConfig.attackStrategy = when(spinner.selectedItemPosition) {
                    1 -> AttackStrategy.TOP_BOTTOM
                    2 -> AttackStrategy.LEFT_RIGHT
                    else -> AttackStrategy.ALL_SIDES
                }
                val maxDelay = etDelay.text.toString().toIntOrNull() ?: 5
                BotConfig.delayMaxMs = (maxDelay * 1000).toLong()
                BotLogger.system("Settings disimpan")
                Toast.makeText(this@MainActivity, "Settings tersimpan!", Toast.LENGTH_SHORT).show()
            }
        })

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun showArmyTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(sectionHeader("ARMY SETUP"))
        ll.addView(TextView(this).apply {
            text = "Troops per sisi:"
            textSize = 13f
            setTextColor(Color.WHITE)
        })

        val troopsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTroops = TextView(this).apply {
            text = BotConfig.troopsPerSide.toString()
            textSize = 18f
            setTextColor(Color.parseColor("#FFD700"))
            setPadding(20, 0, 20, 0)
        }
        troopsRow.addView(Button(this).apply {
            text = "-"
            setBackgroundColor(Color.parseColor("#CC0000"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(80, 80)
            setOnClickListener {
                if (BotConfig.troopsPerSide > 1) {
                    BotConfig.troopsPerSide--
                    tvTroops.text = BotConfig.troopsPerSide.toString()
                }
            }
        })
        troopsRow.addView(tvTroops)
        troopsRow.addView(Button(this).apply {
            text = "+"
            setBackgroundColor(Color.parseColor("#00AA44"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(80, 80)
            setOnClickListener {
                BotConfig.troopsPerSide++
                tvTroops.text = BotConfig.troopsPerSide.toString()
            }
        })
        ll.addView(troopsRow)

        ll.addView(TextView(this).apply {
            text = "\nInfo: Bot akan deploy ${BotConfig.troopsPerSide} troops di tiap sisi (atas, bawah, kiri, kanan)"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        })

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun showCalibrationTab() {
        tabContent.removeAllViews()
        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        ll.addView(sectionHeader("NAVIGATION"))
        val navTemplates = listOf(
            "attack.png" to "Main attack sword icon",
            "find_match.png" to "Find opponent button",
            "attack2.png" to "Confirm attack button",
            "next.png" to "Skip enemy base",
            "returnhome.png" to "Go back to base",
            "searching.png" to "Loading/searching cloud"
        )
        navTemplates.forEach { (name, desc) -> ll.addView(templateRow(name, desc)) }

        ll.addView(sectionHeader("OBSTACLE"))
        val obstTemplates = listOf(
            "reload.png" to "Connection error reload",
            "try_again.png" to "Connection retry button",
            "okay.png" to "Generic confirm OK",
            "cancel.png" to "Generic cancel",
            "x_close.png" to "Popup close button",
            "star_bonus.png" to "Star bonus popup header"
        )
        obstTemplates.forEach { (name, desc) -> ll.addView(templateRow(name, desc)) }

        ll.addView(sectionHeader("COLLECTOR TAP"))
        val colTemplates = listOf(
            "gold_col.png" to "Gold collector icon",
            "elixir_col.png" to "Elixir collector icon",
            "dark_col.png" to "Dark elixir drill icon"
        )
        colTemplates.forEach { (name, desc) -> ll.addView(templateRow(name, desc)) }

        ll.addView(sectionHeader("SMART ATTACK"))
        val smartTemplates = listOf(
            "gold1.png" to "Target Gold Musuh 1",
            "elixir1.png" to "Target Elixir Musuh 1",
            "de1.png" to "Target Dark Elixir Musuh 1",
            "battle_result.png" to "Battle result screen"
        )
        smartTemplates.forEach { (name, desc) -> ll.addView(templateRow(name, desc)) }

        sv.addView(ll)
        tabContent.addView(sv)
    }

    private fun templateRow(name: String, desc: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(12, 12, 12, 12)
            val margin = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 4 }
            layoutParams = margin
            gravity = Gravity.CENTER_VERTICAL

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = name
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = desc
                    textSize = 11f
                    setTextColor(Color.parseColor("#888888"))
                })
                addView(TextView(this@MainActivity).apply {
                    text = "Default Asset (Auto Resize)"
                    textSize = 10f
                    setTextColor(Color.parseColor("#FF6B35"))
                })
            })

            addView(Button(this@MainActivity).apply {
                text = "📷 Gambar"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#334466"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    Toast.makeText(this@MainActivity, "Upload $name ke GitHub assets/templates/", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun sectionHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#FF6B35"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
    }

    private fun inputRow(parent: LinearLayout, label: String, default: String): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 8, 0, 2)
        })
        val et = EditText(this).apply {
            setText(default)
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#16213e"))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(12, 12, 12, 12)
        }
        parent.addView(et)
        return et
    }

    private fun permissionRow(name: String, granted: Boolean, btnText: String, action: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (granted) Color.parseColor("#0d2e1a") else Color.parseColor("#2e1a0d"))
            setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 }
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(this@MainActivity).apply {
                text = "${if (granted) "✅" else "❌"} $name"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            if (!granted) {
                addView(Button(this@MainActivity).apply {
                    text = btnText
                    textSize = 11f
                    setBackgroundColor(Color.parseColor("#FF6B35"))
                    setTextColor(Color.WHITE)
                    setOnClickListener { action() }
                })
            }
        }
    }

    private fun checkPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Aktifkan Overlay permission untuk floating window!", Toast.LENGTH_LONG).show()
        }
    }

    private fun startBot() {
        if (AccessibilityBot.instance == null) {
            Toast.makeText(this, "Aktifkan COC Bot Accessibility dulu!", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        BotService.start(this)
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(pm.createScreenCaptureIntent())
    }

    private fun stopBot() {
        BotService.getInstance()?.stopBot()
        BotService.stop(this)
        ScreenCaptureService.stop(this)
        FloatingWindowService.stop(this)
    }

    private fun togglePause() {
        val bot = BotService.getInstance() ?: return
        if (bot.state.value == BotState.PAUSED) {
            bot.resumeBot(); btnPause.text = "⏸ PAUSE"
        } else {
            bot.pauseBot(); btnPause.text = "▶ RESUME"
        }
    }

    private fun observeBot() {
        if (!::tvLog.isInitialized) return
        lifecycleScope.launch {
            BotLogger.logs.collectLatest { logs ->
                val text = logs.takeLast(80).joinToString("\n") {
                    "[${it.timestamp}] [${it.level.name}] ${it.message}"
                }
                tvLog.text = text
                if (::scrollLog.isInitialized) scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
        lifecycleScope.launch {
            BotService.getInstance()?.state?.collectLatest { state ->
                if (::tvStatus.isInitialized) tvStatus.text = "Status: $state"
            }
        }
        lifecycleScope.launch {
            BotService.getInstance()?.session?.collectLatest { sess ->
                if (::tvStats.isInitialized)
                    tvStats.text = "🏆${sess.totalMatches} 💛${"%,d".format(sess.totalGold)} 💜${"%,d".format(sess.totalElixir)} 🖤${sess.totalDarkElixir}"
            }
        }
        lifecycleScope.launch {
            BotService.getInstance()?.currentLoot?.collectLatest { ld ->
                if (::tvLoot.isInitialized)
                    tvLoot.text = "🎯 G:${"%,d".format(ld.gold)} E:${"%,d".format(ld.elixir)} DE:${ld.darkElixir}"
            }
        }
    }
}
