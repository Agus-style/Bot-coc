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
import com.cocbot.vision.LootData
import com.cocbot.vision.LootScanner
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
    private lateinit var lootScanner: LootScanner

    private val _state = MutableStateFlow(BotState.IDLE)
    val state: StateFlow<BotState> = _state

    private val _session = MutableStateFlow(FarmSession())
    val session: StateFlow<FarmSession> = _session

    private val _currentLoot = MutableStateFlow(LootData())
    val currentLoot: StateFlow<LootData> = _currentLoot

    private var botJob: Job? = null
    private var isPaused = false
    private var currentMatchNumber = 0
    private var nextTapCount = 0
    private var battleStartTime = 0L
    private var troopsWaitStart = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        templateManager = TemplateManager(this)
        lootScanner = LootScanner()
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
        val s = _session.value
        BotLogger.system("Bot dihentikan. Total match: ${s.totalMatches}")
        BotLogger.rekap("TOTAL -> G: ${"%,d".format(s.totalGold)} | E: ${"%,d".format(s.totalElixir)} | DE: ${s.totalDarkElixir}")
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
            if (isPaused) { delay(1000); continue }

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
                BotState.CHECKING_HOME -> stateCheckHome(accessibility)
                BotState.CHECKING_RESOURCES -> stateCheckResources()
                BotState.OPENING_ATTACK -> stateOpenAttack(accessibility)
                BotState.FINDING_MATCH -> stateFindMatch(accessibility)
                BotState.SEARCHING -> stateSearching(accessibility)
                BotState.SCOUTING -> stateScouting(screenshot, accessibility)
                BotState.DEPLOYING_TROOPS -> stateDeployTroops(accessibility)
                BotState.WAITING_BATTLE -> stateWaitBattle(screenshot, accessibility)
                BotState.READING_RESULT -> stateReadResult(screenshot, accessibility)
                BotState.RETURNING_HOME -> stateReturnHome(accessibility)
                BotState.WAITING_TROOPS -> stateWaitTroops()
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

    private suspend fun stateCheckHome(accessibility: AccessibilityBot) {
        BotLogger.info("Tombol Home ditemukan.")
        delay(BotConfig.randomDelay())
        _state.value = BotState.CHECKING_RESOURCES
    }

    private suspend fun stateCheckResources() {
        BotLogger.info("Memeriksa resources...")
        // Cek troops siap via koordinat (tidak pakai template)
        // Langsung ke attack
        _state.value = BotState.OPENING_ATTACK
    }

    private suspend fun stateOpenAttack(accessibility: AccessibilityBot) {
        BotLogger.info("Men-tap tombol Serang!")
        accessibility.tap(BotConfig.BTN_ATTACK)
        delay(BotConfig.randomDelay())
        delay(1500) // tunggu menu attack muncul
        _state.value = BotState.FINDING_MATCH
    }

    private suspend fun stateFindMatch(accessibility: AccessibilityBot) {
        BotLogger.menu("Memulai Cari Lawan Tanding...")
        accessibility.tap(BotConfig.BTN_FIND_MATCH)
        delay(BotConfig.randomDelay())
        delay(3000) // tunggu loading matchmaking
        nextTapCount = 0
        _state.value = BotState.SEARCHING
    }

    private suspend fun stateSearching(accessibility: AccessibilityBot) {
        BotLogger.info("Mencari lawan...")
        // Tunggu base musuh muncul
        delay(4000)
        _state.value = BotState.SCOUTING
    }

    private suspend fun stateScouting(
        screenshot: android.graphics.Bitmap,
        accessibility: AccessibilityBot
    ) {
        // Scan loot jika filter aktif
        if (BotConfig.enableLootFilter) {
            BotLogger.info("Memindai loot lawan...")
            val loot = lootScanner.scanLoot(screenshot)
            _currentLoot.value = loot
            BotLogger.scan("Loot: $loot")

            val meetsTarget = if (BotConfig.useAnyResource) {
                loot.gold >= BotConfig.minGoldTarget ||
                loot.elixir >= BotConfig.minElixirTarget ||
                (BotConfig.minDarkElixirTarget > 0 && loot.darkElixir >= BotConfig.minDarkElixirTarget)
            } else {
                loot.gold >= BotConfig.minGoldTarget &&
                loot.elixir >= BotConfig.minElixirTarget
            }

            if (!meetsTarget && nextTapCount < BotConfig.maxNextTaps) {
                BotLogger.info("Loot kurang (${loot}), skip ke base berikutnya... ($nextTapCount/${BotConfig.maxNextTaps})")
                nextTapCount++
                accessibility.tap(BotConfig.BTN_NEXT)
                delay(BotConfig.randomDelay())
                delay(3000)
                // Tetap di SCOUTING untuk scan loot base berikutnya
                return
            }

            if (nextTapCount >= BotConfig.maxNextTaps) {
                BotLogger.warning("Max skip tercapai, serang base ini")
            } else {
                BotLogger.info("Loot OK! (${loot}), mulai serang!")
            }
        }

        nextTapCount = 0
        _state.value = BotState.DEPLOYING_TROOPS
    }

    private suspend fun stateDeployTroops(accessibility: AccessibilityBot) {
        currentMatchNumber++
        battleStartTime = System.currentTimeMillis()

        val preDeploy = BotConfig.randomDelay()
        BotLogger.antibot("Jeda organik: ${String.format("%.3f", preDeploy / 1000.0)} detik...")
        delay(preDeploy)

        BotLogger.action("Deploy pasukan ke 4 sisi")
        accessibility.deployAllSides(BotConfig.troopsPerSide)

        _state.value = BotState.WAITING_BATTLE
    }

    private suspend fun stateWaitBattle(
        screenshot: android.graphics.Bitmap,
        accessibility: AccessibilityBot
    ) {
        val elapsed = (System.currentTimeMillis() - battleStartTime) / 1000
        BotLogger.wait("Menunggu $elapsed detik di Home sebelum langkah selanjutnya...")

        // Cek battle result via template
        val resultVisible = templateManager.isVisible(screenshot, Template.BATTLE_RESULT)
        if (resultVisible) {
            BotLogger.info("Battle selesai!")
            _state.value = BotState.READING_RESULT
            return
        }

        // Timeout surrender
        if (elapsed > BotConfig.waitBattleSeconds) {
            BotLogger.warning("Battle timeout, surrender...")
            accessibility.tap(BotConfig.BTN_END_BATTLE)
            delay(2000)
            accessibility.tap(BotConfig.BTN_OKAY)
            delay(2000)
        }

        delay(2000)
    }

    private suspend fun stateReadResult(
        screenshot: android.graphics.Bitmap,
        accessibility: AccessibilityBot
    ) {
        BotLogger.info("Memindai statistik loot dari layar Battle Result...")

        // Scan loot hasil battle
        val loot = lootScanner.scanLoot(screenshot)

        val result = FarmResult(
            matchNumber = currentMatchNumber,
            goldGained = loot.gold,
            elixirGained = loot.elixir,
            darkElixirGained = loot.darkElixir
        )

        val session = _session.value
        session.addResult(result)
        _session.value = session

        BotLogger.rekap(
            "Pertandingan #$currentMatchNumber -> " +
            "G: +${"%,d".format(loot.gold)} | " +
            "E: +${"%,d".format(loot.elixir)} | " +
            "DE: +${loot.darkElixir}"
        )

        // Tap OK/close result
        accessibility.tap(BotConfig.BTN_OKAY)
        delay(BotConfig.randomDelay())
        delay(2000)

        _state.value = BotState.RETURNING_HOME
    }

    private suspend fun stateReturnHome(accessibility: AccessibilityBot) {
        BotLogger.action("Men-tap koordinat Kembali ke Rumah")
        accessibility.tap(BotConfig.BTN_RETURN_HOME)
        delay(BotConfig.randomDelay())
        BotLogger.info("Selesai serang. Counter saat ini: $currentMatchNumber")
        BotLogger.wait("Menunggu 3 detik di Home sebelum langkah selanjutnya...")
        delay(3000)
        _state.value = BotState.CHECKING_HOME
    }

    private suspend fun stateWaitTroops() {
        if (troopsWaitStart == 0L) troopsWaitStart = System.currentTimeMillis()
        val elapsed = (System.currentTimeMillis() - troopsWaitStart) / 1000
        BotLogger.wait("Menunggu troops training... ${elapsed}s")
        if (elapsed > BotConfig.waitTroopsSeconds) {
            troopsWaitStart = 0L
            _state.value = BotState.OPENING_ATTACK
        }
        delay(10000)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        lootScanner.close()
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
