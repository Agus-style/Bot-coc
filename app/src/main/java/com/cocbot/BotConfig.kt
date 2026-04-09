package com.cocbot

import android.graphics.PointF

/**
 * Koordinat landscape 1612x720 (Infinix Hot 40i)
 * COC dimainkan landscape
 */
object BotConfig {

    // =====================
    // KOORDINAT TOMBOL
    // =====================

    // Home screen
    val BTN_ATTACK = PointF(100f, 648f)           // Tombol Serang! kiri bawah home

    // Menu attack (setelah tap Serang!)
    val BTN_MULTIPLAYER = PointF(250f, 300f)      // Tab Multipemain
    val BTN_FIND_MATCH = PointF(250f, 505f)       // Cari Lawan Tanding (tombol kuning)

    // Layar scouting base musuh
    val BTN_NEXT = PointF(1496f, 510f)            // Berikutnya (kanan bawah)
    val BTN_ATTACK_CONFIRM = PointF(1290f, 600f)  // Serang! hijau (kanan bawah army screen)

    // Battle
    val BTN_END_BATTLE = PointF(110f, 512f)       // Akhiri Serangan (merah kiri)
    val BTN_RETURN_HOME = PointF(760f, 580f)      // Kembali ke Rumah
    val BTN_OKAY = PointF(760f, 500f)             // OK generic
    val BTN_CLOSE_X = PointF(1390f, 55f)          // Tombol X merah tutup panel

    // =====================
    // AREA DEPLOY TROOPS
    // Landscape 1612x720
    // =====================
    // Deploy di 4 sisi tepi layar
    val DEPLOY_TOP_START = PointF(300f, 75f)
    val DEPLOY_TOP_END = PointF(1300f, 75f)
    val DEPLOY_BOTTOM_START = PointF(300f, 645f)
    val DEPLOY_BOTTOM_END = PointF(1300f, 645f)
    val DEPLOY_LEFT_START = PointF(75f, 150f)
    val DEPLOY_LEFT_END = PointF(75f, 570f)
    val DEPLOY_RIGHT_START = PointF(1537f, 150f)
    val DEPLOY_RIGHT_END = PointF(1537f, 570f)

    // =====================
    // LOOT FILTER
    // =====================
    var minGoldTarget = 300_000L
    var minElixirTarget = 300_000L
    var minDarkElixirTarget = 0L
    var useAnyResource = true           // true = cukup salah satu
    var enableLootFilter = true
    var maxNextTaps = 8                 // Max skip sebelum serang paksa

    // =====================
    // TIMING (ms)
    // =====================
    var delayAfterTap = 800L            // Delay setelah tap biasa
    var delayMenuLoad = 2000L           // Tunggu menu muncul
    var delayMatchLoad = 5000L          // Tunggu matchmaking
    var delayBattleCheck = 2000L        // Interval cek battle selesai
    var waitBattleSeconds = 200         // Max tunggu battle
    var waitTroopsSeconds = 600         // Max tunggu troops
    var troopsPerSide = 5               // Troops per sisi

    // Anti-bot random delay
    var delayMinMs = 500L
    var delayMaxMs = 1500L

    fun randomDelay(): Long {
        return delayMinMs + (Math.random() * (delayMaxMs - delayMinMs)).toLong()
    }
}
