package com.cocbot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cocbot.service.AccessibilityBot
import com.cocbot.service.BotService
import com.cocbot.service.ScreenCaptureService
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
    private lateinit var etMinGold: EditText
    private lateinit var etMinElixir: EditText
    private lateinit var etMinDark: EditText
    private lateinit var switchFilter: Switch

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            ScreenCaptureService.start(this, result.resultCode, data)
            android.os.Handler(mainLooper).postDelayed({
                BotService.getInstance()?.startBot()
            }, 1000)
        } else {
            Toast.makeText(this, "Permission ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
        }

        // Title
        root.addView(TextView(this).apply {
            text = "⚔️ COC Auto Farming Bot"
            textSize = 18f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })

        // Status
        tvStatus = TextView(this).apply {
            text = "Status: IDLE"
            textSize = 13f
            setTextColor(Color.parseColor("#00FF88"))
        }
        root.addView(tvStatus)

        // Stats
        tvStats = TextView(this).apply {
            text = "🏆 Match: 0  💛 Gold: 0  💜 Elixir: 0"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(12, 8, 12, 8)
        }
        root.addView(tvStats)

        // Current loot saat scouting
        tvLoot = TextView(this).apply {
            text = "🎯 Loot scan: -"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD700"))
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(12, 4, 12, 8)
        }
        root.addView(tvLoot)

        // ===== LOOT FILTER SETTINGS =====
        root.addView(TextView(this).apply {
            text = "⚙️ Filter Loot"
            textSize = 13f
            setTextColor(Color.parseColor("#FFD700"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 12, 0, 4)
        })

        // Switch filter aktif/nonaktif
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        switchRow.addView(TextView(this).apply {
            text = "Aktifkan filter loot"
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        switchFilter = Switch(this).apply {
            isChecked = BotConfig.enableLootFilter
            setTextColor(Color.WHITE)
        }
        switchRow.addView(switchFilter)
        root.addView(switchRow)

        // Min Gold
        val goldRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        goldRow.addView(TextView(this).apply {
            text = "💛 Min Gold:"
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        etMinGold = EditText(this).apply {
            setText(BotConfig.minGoldTarget.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#16213e"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        goldRow.addView(etMinGold)
        root.addView(goldRow)

        // Min Elixir
        val elixirRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        elixirRow.addView(TextView(this).apply {
            text = "💜 Min Elixir:"
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        etMinElixir = EditText(this).apply {
            setText(BotConfig.minElixirTarget.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#16213e"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        elixirRow.addView(etMinElixir)
        root.addView(elixirRow)

        // Min Dark Elixir
        val darkRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        darkRow.addView(TextView(this).apply {
            text = "🖤 Min Dark:"
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        etMinDark = EditText(this).apply {
            setText(BotConfig.minDarkElixirTarget.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#16213e"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        darkRow.addView(etMinDark)
        root.addView(darkRow)

        // Apply settings button
        root.addView(Button(this).apply {
            text = "💾 Simpan Setting"
            setBackgroundColor(Color.parseColor("#334466"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener { applySettings() }
        })

        // ===== BUTTONS =====
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 4)
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

        root.addView(btnRow)

        // Log area
        root.addView(TextView(this).apply {
            text = "📋 LOG"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD700"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 2)
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.parseColor("#0d0d1a"))
            addView(tvLog)
        }
        root.addView(scrollLog)

        // Bottom stats bar
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#16213e"))
            setPadding(12, 6, 12, 6)
            addView(TextView(this@MainActivity).apply {
                id = android.R.id.text1
                text = "Gold: 0"
                textSize = 11f
                setTextColor(Color.parseColor("#FFD700"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Elixir: 0"
                textSize = 11f
                setTextColor(Color.parseColor("#CC77FF"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Jmlh Farming: 0"
                textSize = 11f
                setTextColor(Color.parseColor("#00FF88"))
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        })

        setContentView(root)
        observeBot()
    }

    private fun applySettings() {
        BotConfig.enableLootFilter = switchFilter.isChecked
        BotConfig.minGoldTarget = etMinGold.text.toString().toLongOrNull() ?: 300_000L
        BotConfig.minElixirTarget = etMinElixir.text.toString().toLongOrNull() ?: 300_000L
        BotConfig.minDarkElixirTarget = etMinDark.text.toString().toLongOrNull() ?: 0L
        Toast.makeText(this, "Setting tersimpan!", Toast.LENGTH_SHORT).show()
        BotLogger.system("Setting update: Min Gold=${BotConfig.minGoldTarget}, Min Elixir=${BotConfig.minElixirTarget}, Min Dark=${BotConfig.minDarkElixirTarget}")
    }

    private fun startBot() {
        if (AccessibilityBot.instance == null) {
            Toast.makeText(this, "Aktifkan COC Bot Accessibility dulu!", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        applySettings()
        BotService.start(this)
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(pm.createScreenCaptureIntent())
    }

    private fun stopBot() {
        BotService.getInstance()?.stopBot()
        BotService.stop(this)
        ScreenCaptureService.stop(this)
    }

    private fun togglePause() {
        val bot = BotService.getInstance() ?: return
        if (bot.state.value == BotState.PAUSED) {
            bot.resumeBot()
            btnPause.text = "⏸ PAUSE"
        } else {
            bot.pauseBot()
            btnPause.text = "▶ RESUME"
        }
    }

    private fun observeBot() {
        lifecycleScope.launch {
            BotLogger.logs.collectLatest { logs ->
                val text = logs.takeLast(60).joinToString("\n") {
                    "[${it.timestamp}] [${it.level.name}] ${it.message}"
                }
                tvLog.text = text
                scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }

        lifecycleScope.launch {
            BotService.getInstance()?.state?.collectLatest { state ->
                tvStatus.text = "Status: $state"
            }
        }

        lifecycleScope.launch {
            BotService.getInstance()?.session?.collectLatest { session ->
                tvStats.text = "🏆 ${session.totalMatches}  💛 ${"%,d".format(session.totalGold)}  💜 ${"%,d".format(session.totalElixir)}"
            }
        }

        lifecycleScope.launch {
            BotService.getInstance()?.currentLoot?.collectLatest { loot ->
                tvLoot.text = "🎯 Scan: G=${"%,d".format(loot.gold)} E=${"%,d".format(loot.elixir)} DE=${loot.darkElixir}"
            }
        }
    }
}
