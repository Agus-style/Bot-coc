package com.cocbot.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log

enum class Template(val fileName: String) {
    HOME_SCREEN("home_screen.png"),
    BTN_ATTACK("btn_attack.png"),
    BTN_FIND_MATCH("btn_find_match.png"),
    BTN_NEXT("btn_next.png"),
    BTN_END_BATTLE("btn_end_battle.png"),
    BTN_RETURN_HOME("btn_return_home.png"),
    BTN_OKAY("btn_okay.png"),
    BATTLE_RESULT("battle_result.png"),
    TROOPS_READY("troops_ready.png"),
    BUILDER_AVAILABLE("builder_available.png"),
    UPGRADE_WALL("logo_up.png"),
    SEARCHING_INDICATOR("searching.png"),
    GOLD_STORAGE("gold_storage.png"),
    ELIXIR_STORAGE("elixir_storage.png"),
}

data class MatchResult(
    val found: Boolean,
    val position: PointF = PointF(0f, 0f),
    val confidence: Double = 0.0
)

class TemplateManager(private val context: Context) {

    companion object {
        private const val TAG = "TemplateManager"
        private const val DEFAULT_THRESHOLD = 0.80
    }

    private val templateCache = mutableMapOf<Template, Bitmap>()

    private fun loadTemplate(template: Template): Bitmap? {
        templateCache[template]?.let { return it }
        return try {
            val bitmap = context.assets.open("templates/${template.fileName}")
                .use { android.graphics.BitmapFactory.decodeStream(it) }
            templateCache[template] = bitmap
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Gagal load template: ${template.fileName}", e)
            null
        }
    }

    /**
     * Template matching manual tanpa OpenCV
     * Pakai pixel comparison sederhana
     */
    fun findTemplate(
        screenshot: Bitmap,
        template: Template,
        threshold: Double = DEFAULT_THRESHOLD
    ): MatchResult {
        val tmpl = loadTemplate(template) ?: return MatchResult(false)

        val sw = screenshot.width
        val sh = screenshot.height
        val tw = tmpl.width
        val th = tmpl.height

        if (tw > sw || th > sh) return MatchResult(false)

        var bestScore = 0.0
        var bestX = 0
        var bestY = 0

        // Sample setiap 4 pixel biar lebih cepat
        val step = 4
        val tmplPixels = IntArray(tw * th)
        tmpl.getPixels(tmplPixels, 0, tw, 0, 0, tw, th)

        for (y in 0..(sh - th) step step) {
            for (x in 0..(sw - tw) step step) {
                val score = calcScore(screenshot, tmplPixels, x, y, tw, th)
                if (score > bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
            }
        }

        return if (bestScore >= threshold) {
            Log.d(TAG, "[SCAN] '${template.fileName}' terdeteksi! (Akurasi: ${"%.1f".format(bestScore * 100)}%)")
            MatchResult(true, PointF((bestX + tw / 2).toFloat(), (bestY + th / 2).toFloat()), bestScore)
        } else {
            Log.d(TAG, "[SCAN] '${template.fileName}' tidak ditemukan (${"%.1f".format(bestScore * 100)}%)")
            MatchResult(false)
        }
    }

    private fun calcScore(screenshot: Bitmap, tmplPixels: IntArray, offX: Int, offY: Int, tw: Int, th: Int): Double {
        var match = 0
        var total = 0
        val step = 4
        for (ty in 0 until th step step) {
            for (tx in 0 until tw step step) {
                val tp = tmplPixels[ty * tw + tx]
                val sp = screenshot.getPixel(offX + tx, offY + ty)
                val dr = Math.abs(android.graphics.Color.red(tp) - android.graphics.Color.red(sp))
                val dg = Math.abs(android.graphics.Color.green(tp) - android.graphics.Color.green(sp))
                val db = Math.abs(android.graphics.Color.blue(tp) - android.graphics.Color.blue(sp))
                if (dr + dg + db < 60) match++
                total++
            }
        }
        return if (total == 0) 0.0 else match.toDouble() / total
    }

    fun isVisible(screenshot: Bitmap, template: Template, threshold: Double = DEFAULT_THRESHOLD): Boolean {
        return findTemplate(screenshot, template, threshold).found
    }

    fun findAny(screenshot: Bitmap, vararg templates: Template): Pair<Template?, MatchResult> {
        for (template in templates) {
            val result = findTemplate(screenshot, template)
            if (result.found) return Pair(template, result)
        }
        return Pair(null, MatchResult(false))
    }

    fun clearCache() {
        templateCache.values.forEach { it.recycle() }
        templateCache.clear()
    }
}
