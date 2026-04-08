package com.cocbot

import android.graphics.PointF

/**
 * Koordinat berdasarkan resolusi Infinix Hot 40i landscape: 1612 x 720
 * COC dimainkan landscape
 */
object BotConfig {

    // =====================
    // KOORDINAT TOMBOL
    // Semua koordinat landscape 1612x720
    // =====================
    val BTN_ATTACK = PointF(113f, 648f)           // Tombol Serang! kiri bawah
    val BTN_FIND_MATCH = PointF(250f, 505f)       // Cari Lawan Tanding
    val BTN_NEXT = PointF(1496f, 510f)            // Berikutnya (kanan bawah)
    val BTN_END_BATTLE = PointF(110f, 512f)       // Akhiri Serangan (merah kiri)
    val BTN_RETURN_HOME = PointF(806f, 600f)      // Kembali ke Rumah
    val BTN_OKAY = PointF(806f, 500f)             // OK generic
    val BTN_ATTACK_CONFIRM = PointF(1496f, 600f)  // Tombol Serang! di layar army (hijau)

    // =====================
    // AREA DEPLOY TROOPS
    // Landscape 1612x720
    // =====================
    val DEPLOY_TOP = Pair(PointF(400f, 80f), PointF(1200f, 80f))
    val DEPLOY_BOTTOM = Pair(PointF(400f, 640f), PointF(1200f, 640f))
    val DEPLOY_LEFT = Pair(PointF(80f, 200f), PointF(80f, 520f))
    val DEPLOY_RIGHT = Pair(PointF(1530f, 200f), PointF(1530f, 520f))

    // =====================
    // LOOT FILTER SETTINGS
    // =====================
    var minGoldTarget = 300_000L        // Min gold untuk serang
    var minElixirTarget = 300_000L      // Min elixir untuk serang
    var minDarkElixirTarget = 0L        // Min dark elixir (0 = tidak filter)
    var useAnyResource = true           // true = cukup salah satu memenuhi
                                        // false = semua harus memenuhi

    // =====================
    // FARMING SETTINGS
    // =====================
    var maxNextTaps = 8                 // Max skip base sebelum serang paksa
    var troopsPerSide = 6               // Troops per sisi
    var waitBattleSeconds = 200         // Timeout battle
    var waitTroopsSeconds = 600         // Tunggu troops training max 10 menit
    var autoUpgradeWall = false         // Auto upgrade wall
    var enableLootFilter = true         // Aktifkan filter loot

    // Anti-bot delay
    var delayMinMs = 700L
    var delayMaxMs = 2200L

    fun randomDelay(): Long {
        return delayMinMs + (Math.random() * (delayMaxMs - delayMinMs)).toLong()
    }
}
