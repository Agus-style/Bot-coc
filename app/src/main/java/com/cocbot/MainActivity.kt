package com.cocbot

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
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

        // Build UI programmatically - no viewBinding needed
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#1a1a2e"))
        }

        val tvTitle = TextView(this).apply {
            text = "⚔️ COC Auto Farming Bot"
            textSize = 20f
            setTextColor(android.graphics.Color.parseColor("#FFD700"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvStatus = TextView(this).apply {
            text = "Status: IDLE"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#00FF88"))
            setPadding(0, 0, 0, 8)
        }

        val tvStats = TextView(this).apply {
            text = "🏆 Match: 0\n💛 Gold: 0\n💜 Elixir: 0"
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#16213e"))
            setPadding(16, 16, 16, 16)
        }

        val btnRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 8)
        }

        val btnStart = Button(this).apply {
            text = "▶ START"
            setBackgroundColor(android.graphics.Color.parseColor("#00AA44"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
        }

        val btnPause = Button(this).apply {
            text = "⏸ PAUSE"
            setBackgroundColor(android.graphics.Color.parseColor("#FF8800"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
        }

        val btnStop = Button(this).apply {
            text = "⏹ STOP"
            setBackgroundColor(android.graphics.Color.parseColor("#CC0000"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvLogLabel = TextView(this).apply {
            text = "📋 LOG"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#FFD700"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 4)
        }

        val tvLog = TextView(this).apply {
            text = ""
            textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#00FF88"))
            setBackgroundColor(android.graphics.Color.parseColor("#0d0d1a"))
            setPadding(16, 16, 16, 16)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val scrollLog = ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(android.graphics.Color.parseColor("#0d0d1a"))
            addView(tvLog)
        }

        btnRow.addView(btnStart)
        btnRow.addView(btnPause)
        btnRow.addView(btnStop)

        root.addView(tvTitle)
        root.addView(tvStatus)
        root.addView(tvStats)
        root.addView(btnRow)
        root.addView(tvLogLabel)
        root.addView(scrollLog)

        setContentView(root)

        // Button listeners
        btnStart.setOnClickListener {
            if (AccessibilityBot.instance == null) {
                Toast.makeText(this, "Aktifkan Accessibility Service dulu!", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            BotService.start(this)
            val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(pm.createScreenCaptureIntent())
        }

        btnStop.setOnClickListener {
            BotService.getInstance()?.stopBot()
            BotService.stop(this)
            ScreenCaptureService.stop(this)
        }

        btnPause.setOnClickListener {
            val bot = BotService.getInstance() ?: return@setOnClickListener
            if (bot.state.value == BotState.PAUSED) {
                bot.resumeBot()
                btnPause.text = "⏸ PAUSE"
            } else {
                bot.pauseBot()
                btnPause.text = "▶ RESUME"
            }
        }

        // Observe logs
        lifecycleScope.launch {
            BotLogger.logs.collectLatest { logs ->
                val text = logs.takeLast(50).joinToString("\n") {
                    "[${it.timestamp}] [${it.level.name}] ${it.message}"
                }
                tvLog.text = text
                scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }

        // Observe state
        lifecycleScope.launch {
            BotService.getInstance()?.state?.collectLatest { state ->
                tvStatus.text = "Status: $state"
            }
        }

        // Observe session
        lifecycleScope.launch {
            BotService.getInstance()?.session?.collectLatest { session ->
                tvStats.text = "🏆 Match: ${session.totalMatches}\n💛 Gold: ${"%,d".format(session.totalGold)}\n💜 Elixir: ${"%,d".format(session.totalElixir)}"
            }
        }
    }
}
