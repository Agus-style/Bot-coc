package com.cocbot.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Semua template yang bisa dideteksi
 */
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
        private const val HIGH_THRESHOLD = 0.90
    }

    private val templateCache = mutableMapOf<Template, Mat>()

    init {
        // Load OpenCV
        System.loadLibrary("opencv_java4")
    }

    /**
     * Load template dari assets ke cache
     */
    private fun loadTemplate(template: Template): Mat? {
        if (templateCache.containsKey(template)) {
            return templateCache[template]
        }

        return try {
            val bitmap = context.assets.open("templates/${template.fileName}")
                .use { android.graphics.BitmapFactory.decodeStream(it) }
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY)
            templateCache[template] = mat
            bitmap.recycle()
            mat
        } catch (e: Exception) {
            Log.e(TAG, "Gagal load template: ${template.fileName}", e)
            null
        }
    }

    /**
     * Cari template di screenshot
     * @return MatchResult dengan posisi tengah template jika ditemukan
     */
    fun findTemplate(
        screenshot: Bitmap,
        template: Template,
        threshold: Double = DEFAULT_THRESHOLD,
        searchRegion: Rect? = null
    ): MatchResult {
        val templateMat = loadTemplate(template) ?: return MatchResult(false)

        val screenshotMat = Mat()
        Utils.bitmapToMat(screenshot, screenshotMat)
        Imgproc.cvtColor(screenshotMat, screenshotMat, Imgproc.COLOR_RGBA2GRAY)

        // Potong region jika ada
        val searchMat = if (searchRegion != null) {
            Mat(screenshotMat, searchRegion)
        } else {
            screenshotMat
        }

        val result = Mat()
        Imgproc.matchTemplate(searchMat, templateMat, result, Imgproc.TM_CCOEFF_NORMED)

        val mmResult = Core.minMaxLoc(result)
        val confidence = mmResult.maxVal

        screenshotMat.release()
        result.release()
        if (searchRegion != null) searchMat.release()

        return if (confidence >= threshold) {
            val matchLoc = mmResult.maxLoc
            val offsetX = searchRegion?.x?.toDouble() ?: 0.0
            val offsetY = searchRegion?.y?.toDouble() ?: 0.0

            val centerX = (matchLoc.x + offsetX + templateMat.cols() / 2).toFloat()
            val centerY = (matchLoc.y + offsetY + templateMat.rows() / 2).toFloat()

            Log.d(TAG, "[SCAN] '${template.fileName}' terdeteksi! (Akurasi: ${"%.1f".format(confidence * 100)}%)")
            MatchResult(true, PointF(centerX, centerY), confidence)
        } else {
            Log.d(TAG, "[SCAN] '${template.fileName}' tidak ditemukan (${("%.1f".format(confidence * 100))}%)")
            MatchResult(false)
        }
    }

    /**
     * Cek apakah template ada di layar (tanpa butuh posisi)
     */
    fun isVisible(screenshot: Bitmap, template: Template, threshold: Double = DEFAULT_THRESHOLD): Boolean {
        return findTemplate(screenshot, template, threshold).found
    }

    /**
     * Cari beberapa template sekaligus, return yang pertama ditemukan
     */
    fun findAny(screenshot: Bitmap, vararg templates: Template): Pair<Template?, MatchResult> {
        for (template in templates) {
            val result = findTemplate(screenshot, template)
            if (result.found) return Pair(template, result)
        }
        return Pair(null, MatchResult(false))
    }

    /**
     * OCR sederhana untuk baca angka resource
     * Menggunakan region tertentu dari screenshot
     */
    fun readResourceValue(screenshot: Bitmap, region: Rect): Long {
        // TODO: Implementasi OCR dengan ML Kit atau Tesseract
        // Untuk sekarang return 0 dulu
        return 0L
    }

    fun clearCache() {
        templateCache.values.forEach { it.release() }
        templateCache.clear()
    }
}
