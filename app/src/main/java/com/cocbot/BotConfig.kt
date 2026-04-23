package com.cocbot

import android.graphics.PointF

object BotConfig {

    // Koordinat landscape 1612x720
    val BTN_ATTACK = PointF(100f, 648f)
    val BTN_FIND_MATCH = PointF(281f, 528f)
    val BTN_NEXT = PointF(1427f, 500f)
    val BTN_ATTACK_CONFIRM = PointF(1320f, 642f)
    val BTN_END_BATTLE = PointF(159f, 544f)
    val BTN_RETURN_HOME = PointF(814f, 624f)
    val BTN_OKAY = PointF(760f, 500f)
    val BTN_CLOSE_X = PointF(1390f, 55f)

    // Deploy area
    val DEPLOY_TOP_START = PointF(300f, 75f)
    val DEPLOY_TOP_END = PointF(1300f, 75f)
    val DEPLOY_BOTTOM_START = PointF(300f, 560f)
    val DEPLOY_BOTTOM_END = PointF(1300f, 560f)
    val DEPLOY_LEFT_START = PointF(75f, 150f)
    val DEPLOY_LEFT_END = PointF(75f, 480f)
    val DEPLOY_RIGHT_START = PointF(1537f, 150f)
    val DEPLOY_RIGHT_END = PointF(1537f, 480f)

    // Loot filter
    var minGoldTarget = 300_000L
    var minElixirTarget = 300_000L
    var minDarkElixirTarget = 0L
    var useAnyResource = true
    var enableLootFilter = true
    var maxNextTaps = 8

    // Attack strategy
    var attackStrategy = AttackStrategy.ALL_SIDES
    var troopsPerSide = 5

    // Collector
    var autoCollect = true

    // Wall upgrade
    var autoWallUpgrade = false
    var maxGoldForWall = 4_000_000L
    var maxElixirForWall = 4_000_000L

    // Battle
    var autoEndBattle = false
    var waitBattleSeconds = 200
    var waitTroopsSeconds = 600

    // Timing
    var delayMenuLoad = 1500L
    var delayMatchLoad = 4000L
    var delayBattleCheck = 2000
    var delayMinMs = 400L
    var delayMaxMs = 1000L
    var maxRandomDelay = 5

    fun randomDelay(): Long {
        return delayMinMs + (Math.random() * (delayMaxMs - delayMinMs)).toLong()
    }
}

enum class AttackStrategy {
    ALL_SIDES,      // Deploy 4 sisi
    SMART_ZONE,     // Pilih zona terbaik
    TOP_BOTTOM,     // Atas bawah saja
    LEFT_RIGHT      // Kiri kanan saja
}
