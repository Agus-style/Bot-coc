package com.cocbot.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
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
        private const val NOTIF_ID = 1002
        private const val NOTIF_CHANNEL = "bot_service"
        private var instance: BotService? = null
        fun getInstance(): BotService? = instance
        fun start(context: Context) = context.startForegroundService(Intent(context, BotService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, BotService::class.java))
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
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
    private var matchCount = 0
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
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    fun startBot() {
        if (botJob?.isActive == true) return
        isPaused = false
        _session.value = FarmSession()
        BotLogger.system("Bot farming dimulai")
        botJob = scope.launch { runLoop() }
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

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            if (isPaused) {
                delay(1000)
                continue
            }

            val acc = AccessibilityBot.instance
            if (acc == null) {
                BotLogger.error("AccessibilityService tidak aktif!")
                delay(3000)
                continue
            }

            val screenshot = ScreenCaptureService.getInstance()?.captureScreen()
            if (screenshot == null) {
                BotLogger.error("Gagal ambil screenshot - pastikan COC di foreground")
                delay(2000)
                continue
            }

            val currentState = _state.value
            when (currentState) {
                BotState.IDLE -> {
                    _state.value = BotState.CHECKING_HOME
                }
                BotState.CHECKING_HOME -> {
                    handleCheckHome(acc)
                }
                BotState.OPENING_ATTACK -> {
                    handleOpenAttack(acc)
                }
                BotState.FINDING_MATCH -> {
                    handleFindMatch(acc)
                }
                BotState.SEARCHING -> {
                    handleSearching()
                }
                BotState.SCOUTING -> {
                    handleScouting(screenshot, acc)
                }
                BotState.DEPLOYING_TROOPS -> {
                    handleDeployTroops(acc)
                }
                BotState.WAITING_BATTLE -> {
                    handleWaitBattle(screenshot, acc)
                }
                BotState.READING_RESULT -> {
                    handleReadResult(screenshot, acc)
                }
                BotState.RETURNING_HOME -> {
                    handleReturnHome(acc)
                }
                BotState.WAITING_TROOPS -> {
                    handleWaitTroops()
                }
                BotState.ERROR -> {
                    BotLogger.error("State ERROR, recovery...")
                    delay(5000)
                    _state.value = BotState.CHECKING_HOME
                }
                BotState.PAUSED -> {
                    delay(1000)
                }
                else -> {
                    delay(500)
                }
            }

            delay(300)
        }
    }

    private suspend fun handleCheckHome(acc: AccessibilityBot) {
        BotLogger.info("Tombol Home ditemukan.")
        delay(BotConfig.randomDelay())
        _state.value = BotState.OPENING_ATTACK
    }

    private suspend fun handleOpenAttack(acc: AccessibilityBot) {
        BotLogger.info("Men-tap tombol Serang!")
        acc.tap(BotConfig.BTN_ATTACK)
        delay(BotConfig.delayMenuLoad)
        _state.value = BotState.FINDING_MATCH
    }

    private suspend fun handleFindMatch(acc: AccessibilityBot) {
        BotLogger.menu("Memulai Cari Lawan Tanding...")
        acc.tap(BotConfig.BTN_FIND_MATCH)
        delay(BotConfig.delayMatchLoad)
        nextTapCount = 0
        _state.value = BotState.SEARCHING
    }

    private suspend fun handleSearching() {
        BotLogger.info("Mencari lawan...")
        delay(3000)
        _state.value = BotState.SCOUTING
    }

    private suspend fun handleScouting(screenshot: android.graphics.Bitmap, acc: AccessibilityBot) {
        if (BotConfig.enableLootFilter) {
            BotLogger.info("Memindai loot lawan...")
            val loot = lootScanner.scanLoot(screenshot)
            _currentLoot.value = loot
            BotLogger.scan("Loot: G=${"%,d".format(loot.gold)} | E=${"%,d".format(loot.elixir)} | DE=${loot.darkElixir}")

            val ok = checkLootTarget(loot)

            if (!ok && nextTapCount < BotConfig.maxNextTaps) {
                nextTapCount++
                BotLogger.info("Loot kurang, skip... ($nextTapCount/${BotConfig.maxNextTaps})")
                acc.tap(BotConfig.BTN_NEXT)
                delay(BotConfig.delayMatchLoad)
                // tetap di SCOUTING, tidak ganti state
                return
            }

            if (nextTapCount >= BotConfig.maxNextTaps) {
                BotLogger.warning("Max skip tercapai, serang paksa!")
            } else {
                BotLogger.info("Loot OK! Mulai serang!")
            }
        }

        nextTapCount = 0
        BotLogger.action("Men-tap Serang! (konfirmasi)")
        acc.tap(BotConfig.BTN_ATTACK_CONFIRM)
        delay(BotConfig.delayMenuLoad)
        _state.value = BotState.DEPLOYING_TROOPS
    }

    private suspend fun handleDeployTroops(acc: AccessibilityBot) {
        matchCount++
        battleStartTime = System.currentTimeMillis()

        val pre = BotConfig.randomDelay()
        BotLogger.antibot("Jeda organik: ${String.format("%.3f", pre / 1000.0)} detik...")
        delay(pre)

        BotLogger.action("Deploy pasukan ke 4 sisi")
        deployTroops(acc)
        _state.value = BotState.WAITING_BATTLE
    }

    private suspend fun handleWaitBattle(screenshot: android.graphics.Bitmap, acc: AccessibilityBot) {
        val elapsed = (System.currentTimeMillis() - battleStartTime) / 1000
        BotLogger.wait("Menunggu $elapsed detik di Home sebelum langkah selanjutnya...")

        val resultVisible = templateManager.isVisible(screenshot, Template.BATTLE_RESULT)
        if (resultVisible) {
            BotLogger.info("Battle selesai!")
            _state.value = BotState.READING_RESULT
            return
        }

        if (elapsed > BotConfig.waitBattleSeconds) {
            BotLogger.warning("Battle timeout, surrender...")
            acc.tap(BotConfig.BTN_END_BATTLE)
            delay(1500)
            acc.tap(BotConfig.BTN_OKAY)
            delay(2000)
            _state.value = BotState.READING_RESULT
            return
        }

        delay(BotConfig.delayBattleCheck.toLong())
    }

    private suspend fun handleReadResult(screenshot: android.graphics.Bitmap, acc: AccessibilityBot) {
        BotLogger.info("Memindai statistik loot dari layar Battle Result...")
        val loot = lootScanner.scanLoot(screenshot)

        val result = FarmResult(matchCount, loot.gold, loot.elixir, loot.darkElixir)
        val sess = _session.value
        sess.addResult(result)
        _session.value = sess

        BotLogger.rekap(
            "Pertandingan #$matchCount -> " +
            "G: +${"%,d".format(loot.gold)} | " +
            "E: +${"%,d".format(loot.elixir)} | " +
            "DE: +${loot.darkElixir}"
        )

        acc.tap(BotConfig.BTN_OKAY)
        delay(1500)
        acc.tap(BotConfig.BTN_RETURN_HOME)
        delay(BotConfig.randomDelay())

        BotLogger.info("Selesai serang. Counter saat ini: $matchCount")
        BotLogger.wait("Menunggu 3 detik di Home sebelum langkah selanjutnya...")
        delay(3000)

        _state.value = BotState.CHECKING_HOME
    }

    private suspend fun handleReturnHome(acc: AccessibilityBot) {
        acc.tap(BotConfig.BTN_RETURN_HOME)
        delay(BotConfig.randomDelay())
        delay(3000)
        _state.value = BotState.CHECKING_HOME
    }

    private suspend fun handleWaitTroops() {
        if (troopsWaitStart == 0L) troopsWaitStart = System.currentTimeMillis()
        val elapsed = (System.currentTimeMillis() - troopsWaitStart) / 1000
        BotLogger.wait("Menunggu troops training... ${elapsed}s")
        if (elapsed > BotConfig.waitTroopsSeconds) {
            troopsWaitStart = 0L
            _state.value = BotState.OPENING_ATTACK
        }
        delay(10000)
    }

    private fun checkLootTarget(loot: LootData): Boolean {
        return if (BotConfig.useAnyResource) {
            loot.gold >= BotConfig.minGoldTarget ||
            loot.elixir >= BotConfig.minElixirTarget ||
            (BotConfig.minDarkElixirTarget > 0 && loot.darkElixir >= BotConfig.minDarkElixirTarget)
        } else {
            loot.gold >= BotConfig.minGoldTarget &&
            loot.elixir >= BotConfig.minElixirTarget
        }
    }

    private suspend fun deployTroops(acc: AccessibilityBot) {
        val n = BotConfig.troopsPerSide

        BotLogger.action("Deploy sisi atas")
        val topStep = (BotConfig.DEPLOY_TOP_END.x - BotConfig.DEPLOY_TOP_START.x) / n
        for (i in 0 until n) {
            acc.tap(BotConfig.DEPLOY_TOP_START.x + topStep * i, BotConfig.DEPLOY_TOP_START.y)
            delay(150)
        }
        delay(300)

        BotLogger.action("Deploy sisi bawah")
        val botStep = (BotConfig.DEPLOY_BOTTOM_END.x - BotConfig.DEPLOY_BOTTOM_START.x) / n
        for (i in 0 until n) {
            acc.tap(BotConfig.DEPLOY_BOTTOM_START.x + botStep * i, BotConfig.DEPLOY_BOTTOM_START.y)
            delay(150)
        }
        delay(300)

        BotLogger.action("Deploy sisi kiri")
        val leftStep = (BotConfig.DEPLOY_LEFT_END.y - BotConfig.DEPLOY_LEFT_START.y) / n
        for (i in 0 until n) {
            acc.tap(BotConfig.DEPLOY_LEFT_START.x, BotConfig.DEPLOY_LEFT_START.y + leftStep * i)
            delay(150)
        }
        delay(300)

        BotLogger.action("Deploy sisi kanan")
        val rightStep = (BotConfig.DEPLOY_RIGHT_END.y - BotConfig.DEPLOY_RIGHT_START.y) / n
        for (i in 0 until n) {
            acc.tap(BotConfig.DEPLOY_RIGHT_START.x, BotConfig.DEPLOY_RIGHT_START.y + rightStep * i)
            delay(150)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        lootScanner.close()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val ch = NotificationChannel(NOTIF_CHANNEL, "Bot Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, NOTIF_CHANNEL)
        .setContentTitle("COC Bot Farming")
        .setContentText("Bot aktif")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setOngoing(true)
        .build()
}
