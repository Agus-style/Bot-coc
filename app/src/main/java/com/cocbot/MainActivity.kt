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

    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvStats: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnPause: Button
    private lateinit var scrollLog: ScrollView
    private lateinit var btnClearLog: Button

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            ScreenCaptureService.start(this, result.resultCode, data)
            // Mulai bot setelah screen capture ready
            android.os.Handler(mainLooper).postDelayed({
                BotService.getInstance()?.startBot()
            }, 1000)
        } else {
            Toast.makeText(this, "Permission screen capture ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        observeBot()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tv_status)
        tvLog = findViewById(R.id.tv_log)
        tvStats = findViewById(R.id.tv_stats)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)
        btnPause = findViewById(R.id.btn_pause)
        scrollLog = findViewById(R.id.scroll_log)
        btnClearLog = findViewById(R.id.btn_clear_log)
    }

    private fun setupListeners() {
        btnStart.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "Aktifkan Accessibility Service dulu!", Toast.LENGTH_LONG).show()
                openAccessibilitySettings()
                return@setOnClickListener
            }

            // Start BotService dulu
            BotService.start(this)

            // Request screen capture permission
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }

        btnStop.setOnClickListener {
            BotService.getInstance()?.stopBot()
            BotService.stop(this)
            ScreenCaptureService.stop(this)
        }

        btnPause.setOnClickListener {
            val bot = BotService.getInstance() ?: return@setOnClickListener
            val state = bot.state.value
            if (state == BotState.PAUSED) {
                bot.resumeBot()
                btnPause.text = "Pause"
            } else {
                bot.pauseBot()
                btnPause.text = "Resume"
            }
        }

        btnClearLog.setOnClickListener {
            BotLogger.clear()
        }
    }

    private fun observeBot() {
        // Observe logs
        lifecycleScope.launch {
            BotLogger.logs.collectLatest { logs ->
                val logText = logs.takeLast(50).joinToString("\n") { entry ->
                    "[${entry.timestamp}] [${entry.level.name}] ${entry.message}"
                }
                tvLog.text = logText
                scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }

        // Observe state
        lifecycleScope.launch {
            BotService.getInstance()?.state?.collectLatest { state ->
                tvStatus.text = "Status: $state"
            }
        }

        // Observe session stats
        lifecycleScope.launch {
            BotService.getInstance()?.session?.collectLatest { session ->
                tvStats.text = """
                    🏆 Match: ${session.totalMatches}
                    💛 Gold: ${"%,d".format(session.totalGold)}
                    💜 Elixir: ${"%,d".format(session.totalElixir)}
                    🖤 Dark Elixir: ${"%,d".format(session.totalDarkElixir)}
                """.trimIndent()
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return AccessibilityBot.instance != null
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
