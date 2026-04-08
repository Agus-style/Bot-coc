package com.cocbot.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cocbot.BotConfig
import com.cocbot.BotLogger
import com.cocbot.state.BotState
import com.cocbot.state.FarmResult
import com.cocbot.state.FarmSession
import com.cocbot.vision.Template
import com.cocbot.vision.TemplateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BotService : Service() {

    companion object {
        private const val TAG = "BotService"
        private const val NOTIF_ID = 1002
        private const val NOTIF_CHANNEL = "bot_service"

        private var instance: BotService? = null
        fun getInstance(): BotService? = instance

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BotService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BotService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var templateManager: TemplateManager

    private val _state = MutableStateFlow(BotState.IDLE)
    val state: StateFlow<BotState> = _state

    private val _session = MutableStateFlow(FarmSession())
    val session: StateFlow<FarmSession> = _session

    private var botJob: Job? = null
    private var isPaused = false

    private var currentMatchNumber = 0
    private var pendingGold = 0L
    private var pendingElixir = 0L
    private var pendingDarkElixir = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        templateManager = TemplateManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Bot berjalan..."))
        return START_STICKY
    }

    fun startBot() {
        if (botJob?.isActive == true) return
        isPaused = false
        _session.value = FarmSession()
        BotLogger.system("Bot farming dimulai")
        botJob = serviceScope.launch { runBotLoop() }
    }

    fun stopBot() {
        botJob?.cancel()
        _state.value = BotState.IDLE
        BotLogger.system("Bot dihentikan. Total match: ${_session.value.totalMatches}")
    }

    fun pauseBot() {
        isPaused = true
        _state.value = BotState.PAUSED
        BotLogger.system("Bot di-pause")
    }

    fun resumeBot() {
        isPaused = false
        BotLogger.system("Bot dilanjutkan")
    }

    private suspend fun runBotLoop() {
        while (currentCoroutineContext().isActive) {
            if (isPaused) {
                delay(1000)
                continue
            }

            val screenshot = ScreenCaptureService.getInstance()?.captureScreen()
            if (screenshot == null) {
                BotLogger.error("Gagal ambil screenshot")
                delay(2000)
                continue
            }

            val accessibility = AccessibilityBot.instance
            if (accessibility == null) {
                BotLogger.error("AccessibilityService tidak aktif!")
                delay(3000)
                continue
            }

            when (_state.value) {
                BotState.IDLE -> _state.value = BotState.CHECKING_HOME
                BotState.CHECKING_HOME -> stateCheckHome(screenshot, accessibility)
                BotState.CHECKING_RESOURCES -> stateCheckResources(screenshot)
                BotState.OPENING_ATTACK -> stateOpenAttack(screenshot, accessibility)
                BotState.FINDING_MATCH -> stateFindMatch(screenshot, accessibility)
                BotState.SEARCHING -> stateSearching(screenshot)
                BotState.SCOUTING -> stateScouting(screenshot)
                BotState.DEPLOYING_TROOPS -> stateDeployTroops(screenshot, accessibility)
                BotState.WAITING_BATTLE -> stateWaitBattle(screenshot, accessibility)
                BotState.READING_RESULT -> stateReadResult(screenshot, accessibility)
                BotState.RETURNING_HOME -> stateReturnHome(screenshot, accessibility)
                BotState.UPGRADING_WALL -> stateUpgradeWall(screenshot, accessibility)
                BotState.WAITING_TROOPS -> stateWaitTroops(screenshot)
                BotState.ERROR -> {
                    BotLogger.error("State ERROR, recovery...")
                    delay(5000)
                    _state.value = BotState.CHECKING_HOME
                }
                BotState.PAUSED -> delay(1000)
                else -> delay(500)
            }

            delay(500)
        }
    }

    private suspend fun stateCheckHome(screenshot: android.graphics.Bitmap, accessibility: AccessibilityBot) {
        BotLogger.info("Memeriksa home screen...")
        val homeResult = templateManager.findTemplate(screenshot, Template.HOME_SCREEN)
        if (homeResult.found) {
            BotLogger.info("Home screen terdeteksi")
            _state.value = BotState.CHECKING_RESOURCES
        } else {
            val returnResult = templateManager.findTemplate(screenshot, Template.BTN_RETURN_HOME)
            if (returnResult.found) {
                BotLogger.action("Men-tap Return Home")
                accessibility.tap(returnResult.position)
                delay(BotConfig.randomDelay())
            } else {
                delay(3000)
            }
        }
    }

    private suspend fun stateCheckResources(screenshot: android.graphics.Bitmap) {
        val troopsReady = templateManager.isVisible(screenshot, Template.TROOPS_READY)
        if (!troopsReady) {
            _state.value = BotState.WAITING_TROOPS
            return
        }
        if (BotConfig.autoUpgradeWall && templateManager.isVisible(screenshot, Template.UPGRADE_WALL)) {
            _state.value = BotState.UPGRADING_WALL
            return
        }
        _state.value = BotState.OPENING_ATTACK
    }

    private suspend fun stateOpenAttack(screenshot: android.graphics.Bitmap, accessibility: AccessibilityBot) {
        BotLogger.info("Mencari tombol Attack...")
        val attackResult = templateManager.findTemplate(screenshot, Template.BTN_ATTACK)
        if (attackResult.found) {
            accessibility.tap(attackResult.position)
        } else {
            accessibility.tap(BotConfig.BTN_ATTACK)
        }
        delay(BotConfig.randomDelay())
        _state.value = BotState.FINDING_MATCH
    }

    private suspend fun stateFindMatch(screenshot: android.graphics.Bitmap, accessibility: AccessibilityBot) {
        val findResult = templateManager.findTemplate(screenshot, Template.BTN_FIND_MATCH)
        if (findResult.found) {
            accessibility.tap(findResult.position)
            delay(BotConfig.randomDelay())
            _state.value = BotState.SEARCHING
        } else {
            delay(2000)
        }
    }

    private suspend fun stateSearching(screenshot: android.graphics.Bitmap) {
        val nextResult = templateManager.findTemplate(screenshot, Template.BTN_NEXT)
        if (nextResult.found) {
            _state.value = BotState.SCOUTING
        } else {
            delay(2000)
        }
    }

    private suspend fun stateScouting(screenshot: android.graphics.Bitmap) {
        _state.value = BotState.DEPLOYING_TROOPS
    }

    private var battleStartTime = 0L

    private suspend fun stateDeployTroops(screenshot: android.graphics.Bitmap, accessibility: AccessibilityBot) {
        currentMatchNumber++
        battleStartTime = System.currentTimeMillis()
        val preDeploy = BotConfig.randomDelay()
        BotLogger.antibot("Jeda organik: ${preDeploy / 1000.0} detik...")
        delay(preDeploy)
        accessibility.deployAllSides(BotConfig.troopsPerSide)
        _state.value = BotState.WAITING_BATTLE
    }

    private suspend fun stateWaitBattle(screenshot: android.graphics.Bitmap, accessibility: AccessibilityBot) {
        val elapsed = (System.currentTimeMillis() - battleStartTime) / 1000
        if (templateManager.isVisible(screenshot, Template.BATTLE_RESULT)) {
            _state.value = BotState.READING_RESULT
            return
        }
        if (elapsed > BotConfig.waitBattleSeconds) {
            val endResult = templateManager.findTemplate(screenshot, Template.BTN_END_BATTLE)
            if (endResult.found) accessibility.tap(endResult.position)
            else accessibility.tap(BotConfig.BTN_END_BATTLE)
            delay(1000)
        }
        delay(2000)
    }

    private suspend fun stateReadResult(screenshot: android.graphics.Bitmap, accessibility: AccessibilityBot) {
        BotLogger.info("Memindai statistik loot...")
        val result = FarmResult(currentMatchNumber, pendingGold, pendingElixir, pendingDarkElixir)
        val session = _session.value
        session.addResult(result)
        _session.value = session
        BotLogger.rekap("Pertandingan #$currentMatchNumber -> G: +${pendingGold} | E: +${pendingElixir} | DE: +${pendingDarkElixir}")
        val okResult = templateManager.findTemplate(screenshot, Template.BTN_OKAY)
        if (okResult.found) accessibility.tap(okResult.position)
        else accessibility.tap(BotConfig.BTN_OKAY)
        delay(BotConfig.randomDelay())
        _state.value = BotState.RETURNING_HOME
    }

    private suspend fun stateReturnHome(screenshot: android.graphics.Bitmap, accessibility: AccessibilityBot) {
        BotLogger.action("Men-tap koordinat Kembali ke Rumah")
        val returnResult = templateManager.findTemplate(screenshot, Template.BTN_RETURN_HOME)
        if (returnResult.found) accessibility.tap(returnResult.position)
        else accessibility.tap(BotConfig.BTN_RETURN_HOME)
        delay(BotConfig.randomDelay())
        BotLogger.wait("Menunggu 3 detik di Home...")
        delay(3000)
        _state.value = BotState.CHECKING_HOME
    }

    private suspend fun stateUpgradeWall(screenshot: android.graphics.Bitmap, accessibility: AccessibilityBot) {
        BotLogger.system("Upgrade Wall...")
        val builderResult = templateManager.findTemplate(screenshot, Template.BUILDER_AVAILABLE)
        if (builderResult.found) accessibility.tap(builderResult.position)
        delay(BotConfig.randomDelay())
        _state.value = BotState.OPENING_ATTACK
    }

    private var troopsWaitStart = 0L

    private suspend fun stateWaitTroops(screenshot: android.graphics.Bitmap) {
        if (troopsWaitStart == 0L) troopsWaitStart = System.currentTimeMillis()
        val elapsed = (System.currentTimeMillis() - troopsWaitStart) / 1000
        if (templateManager.isVisible(screenshot, Template.TROOPS_READY)) {
            troopsWaitStart = 0L
            _state.value = BotState.CHECKING_HOME
            return
        }
        if (elapsed > BotConfig.waitTroopsSeconds) {
            troopsWaitStart = 0L
            _state.value = BotState.OPENING_ATTACK
        }
        delay(10000)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIF_CHANNEL, "Bot Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("COC Bot Farming")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }
}
