package com.cocbot

import android.graphics.PointF

/**
 * Semua koordinat berdasarkan resolusi Infinix Hot 40i: 720 x 1612
 * Koordinat bisa di-adjust via UI settings
 */
object BotConfig {

    // =====================
    // KOORDINAT TOMBOL HOME
    // =====================
    val BTN_ATTACK = PointF(113f, 1530f)          // Tombol Attack kiri bawah
    val BTN_FIND_MATCH = PointF(360f, 1200f)      // Find a Match
    val BTN_NEXT = PointF(600f, 1200f)            // Next (skip base)
    val BTN_END_BATTLE = PointF(360f, 1500f)      // End Battle (surrender)
    val BTN_RETURN_HOME = PointF(360f, 1400f)     // Return Home
    val BTN_OKAY = PointF(360f, 1000f)            // OK/Confirm generic
    val BTN_CLOSE = PointF(650f, 200f)            // Close dialog

    // =====================
    // AREA SUMBER DAYA
    // =====================
    // Region untuk OCR baca nilai gold/elixir
    // Format: left, top, right, bottom
    val REGION_GOLD = android.graphics.Rect(50, 35, 300, 75)
    val REGION_ELIXIR = android.graphics.Rect(50, 75, 300, 115)
    val REGION_DARK_ELIXIR = android.graphics.Rect(50, 115, 300, 150)

    // =====================
    // AREA DEPLOY TROOPS
    // =====================
    // Battle screen 720x1612, area game ~720x900
    val DEPLOY_TOP_LEFT = PointF(150f, 150f)
    val DEPLOY_TOP_RIGHT = PointF(570f, 150f)
    val DEPLOY_BOTTOM_LEFT = PointF(150f, 850f)
    val DEPLOY_BOTTOM_RIGHT = PointF(570f, 850f)

    // =====================
    // FARMING SETTINGS
    // =====================
    var maxNextTaps = 5              // Max skip base sebelum serang
    var troopsPerSide = 5            // Jumlah troops deploy per sisi
    var waitBattleSeconds = 180      // Tunggu battle max 3 menit
    var waitTroopsSeconds = 300      // Tunggu troops training max 5 menit
    var minGoldToFarm = 100_000L     // Min gold untuk mulai farming
    var minElixirToFarm = 100_000L   // Min elixir untuk mulai farming

    // Anti-bot delay (random delay range dalam ms)
    var delayMinMs = 800L
    var delayMaxMs = 2500L

    // Wall upgrade
    var autoUpgradeWall = true
    var minGoldForWall = 1_000_000L  // Min gold sebelum upgrade wall

    /**
     * Random delay untuk anti-bot detection
     */
    fun randomDelay(): Long {
        return delayMinMs + (Math.random() * (delayMaxMs - delayMinMs)).toLong()
    }
}
