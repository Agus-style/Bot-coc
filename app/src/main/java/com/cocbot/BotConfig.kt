package com.cocbot

import android.graphics.PointF

/**
 * Koordinat landscape 1612x720 (Infinix Hot 40i)
 * Semua koordinat dari Pointer Location
 */
object BotConfig {

    // =====================
    // KOORDINAT TOMBOL
    // =====================

    // Home screen
    val BTN_ATTACK = PointF(100f, 648f)           // Tombol Serang! kiri bawah home

    // Menu attack → Tab Multipemain → Cari Lawan Tanding
    val BTN_FIND_MATCH = PointF(281f, 528f)       // Cari Lawan Tanding (X:281, Y:528)

    // Layar scouting base musuh
    val BTN_NEXT = PointF(1427f, 500f)            // Berikutnya (X:1427, Y:500)
    val BTN_ATTACK_CONFIRM = PointF(1320f, 642f)  // Serang! hijau army screen (X:1320, Y:642)

    // Battle screen
    val BTN_END_BATTLE = PointF(159f, 544f)       // Akhiri Serangan (X:159, Y:544)

    // Result screen
    val BTN_RETURN_HOME = PointF(814f, 624f)      // Ke Beranda (X:814, Y:624)
    val BTN_OKAY = PointF(760f, 500f)             // OK generic
    val BTN_CLOSE_X = PointF(1390f, 55f)          // Tombol X merah

    // =====================
    // AREA DEPLOY TROOPS
    // Landscape 1612x720
    // Area battle = tengah layar
    // Deploy di tepi luar base musuh
    // =====================
    val DEPLOY_TOP_START = PointF(300f, 75f)
    val DEPLOY_TOP_END = PointF(1300f, 75f)
    val DEPLOY_BOTTOM_START = PointF(300f, 560f)
    val DEPLOY_BOTTOM_END = PointF(1300f, 560f)
    val DEPLOY_LEFT_START = PointF(75f, 150f)
    val DEPLOY_LEFT_END = PointF(75f, 480f)
    val DEPLOY_RIGHT_START = PointF(1537f, 150f)
    val DEPLOY_RIGHT_END = PointF(1537f, 480f)

    // =====================
    // LOOT FILTER
    // =====================
    var minGoldTarget = 300_000L
    var minElixirTarget = 300_000L
    var minDarkElixirTarget = 0L
    var useAnyResource = true
    var enableLootFilter = true
    var maxNextTaps = 8

    // =====================
    // TIMING (ms)
    // =====================
    var delayAfterTap = 800L
    var delayMenuLoad = 2000L
    var delayMatchLoad = 5000L
    var delayBattleCheck = 2000
    var waitBattleSeconds = 200
    var waitTroopsSeconds = 600
    var troopsPerSide = 5

    // Anti-bot random delay
    var delayMinMs = 500L
    var delayMaxMs = 1500L

    fun randomDelay(): Long {
        return delayMinMs + (Math.random() * (delayMaxMs - delayMinMs)).toLong()
    }
}
