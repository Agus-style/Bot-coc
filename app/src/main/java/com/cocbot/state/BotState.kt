package com.cocbot.state

/**
 * State machine untuk bot farming COC.
 * Setiap state merepresentasikan kondisi game saat ini.
 */
enum class BotState {
    IDLE,               // Bot belum jalan
    CHECKING_HOME,      // Verifikasi di home screen
    CHECKING_RESOURCES, // Cek gold/elixir cukup atau tidak
    OPENING_ATTACK,     // Tap tombol Attack
    FINDING_MATCH,      // Tap Find a Match
    SEARCHING,          // Sedang mencari lawan
    SCOUTING,           // Lihat base lawan (opsional skip)
    DEPLOYING_TROOPS,   // Deploy pasukan
    WAITING_BATTLE,     // Nunggu battle selesai
    READING_RESULT,     // Baca loot dari Battle Result
    RETURNING_HOME,     // Balik ke Home
    UPGRADING_WALL,     // Upgrade wall jika gold cukup
    COLLECTING_BUILDER, // Klik builder yang selesai
    WAITING_TROOPS,     // Nunggu troops training selesai
    ERROR,              // Error state
    PAUSED              // Bot di-pause manual
}

/**
 * Data hasil satu kali farming
 */
data class FarmResult(
    val matchNumber: Int,
    val goldGained: Long,
    val elixirGained: Long,
    val darkElixirGained: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Summary total farming session
 */
data class FarmSession(
    val startTime: Long = System.currentTimeMillis(),
    var totalMatches: Int = 0,
    var totalGold: Long = 0,
    var totalElixir: Long = 0,
    var totalDarkElixir: Long = 0,
    val results: MutableList<FarmResult> = mutableListOf()
) {
    fun addResult(result: FarmResult) {
        results.add(result)
        totalMatches++
        totalGold += result.goldGained
        totalElixir += result.elixirGained
        totalDarkElixir += result.darkElixirGained
    }
}
