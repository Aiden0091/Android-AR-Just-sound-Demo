package com.senograph.ar.core

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class UltraTemplateDetector(private val context: Context) {
    @Volatile private var referenceGray: GrayImage? = null
    @Volatile private var referenceAspect: Float = 1f

    fun clearReference() {
        referenceGray = null
        referenceAspect = 1f
    }

    fun loadReference(uri: Uri) {
        val input = context.contentResolver.openInputStream(uri) ?: run {
            clearReference()
            return
        }
        input.use {
            val bmp = BitmapFactory.decodeStream(it) ?: run {
                clearReference()
                return
            }
            val normalized = ensureArgb(bmp)
            if (normalized !== bmp) bmp.recycle()
            referenceAspect = normalized.width.toFloat() / max(1, normalized.height)
            referenceGray = bitmapToGray(normalized, 192, 192)
                .histogramNormalize()
                .sharpenLight()
        }
    }

    fun matches(frameGray: GrayImage): Boolean {
        val ref = referenceGray ?: return false
        val src = frameGray.histogramNormalize().gaussianBlur3x3().sobelMagnitude().histogramNormalize()

        val frameW = src.width
        val frameH = src.height
        val aspect = referenceAspect

        val scaleCandidates = floatArrayOf(0.16f, 0.20f, 0.25f, 0.32f, 0.40f, 0.50f, 0.64f)
        val rotationCandidates = intArrayOf(-18, -12, -8, -4, 0, 4, 8, 12, 18)
        val rois = listOf(
            RectFrac(0.00f, 0.00f, 1.00f, 1.00f),
            RectFrac(0.00f, 0.00f, 0.86f, 0.86f),
            RectFrac(0.14f, 0.00f, 1.00f, 0.86f),
            RectFrac(0.00f, 0.14f, 0.86f, 1.00f),
            RectFrac(0.08f, 0.08f, 0.92f, 0.92f)
        )

        var best = 0f
        for (rot in rotationCandidates) {
            val refRotated = ref.rotate(rot)
            for (scale in scaleCandidates) {
                val candidateW = (frameW * scale).roundToInt().coerceIn(28, frameW)
                val candidateH = (candidateW / aspect).roundToInt().coerceIn(28, frameH)
                if (candidateW > frameW || candidateH > frameH) continue

                val baseScaled = refRotated.resize(candidateW, candidateH)
                    .histogramNormalize()
                    .sobelMagnitude()
                    .histogramNormalize()

                val strideX = max(3, candidateW / 14)
                val strideY = max(3, candidateH / 14)

                for (roi in rois) {
                    val xMin = (frameW * roi.left).roundToInt().coerceIn(0, frameW - 1)
                    val yMin = (frameH * roi.top).roundToInt().coerceIn(0, frameH - 1)
                    val xMax = (frameW * roi.right).roundToInt().coerceIn(candidateW, frameW)
                    val yMax = (frameH * roi.bottom).roundToInt().coerceIn(candidateH, frameH)

                    var y = yMin
                    while (y + candidateH <= yMax) {
                        var x = xMin
                        while (x + candidateW <= xMax) {
                            val patch = src.extract(x, y, candidateW, candidateH).histogramNormalize()
                            val score = scorePatch(baseScaled.values, patch.values)
                            if (score > best) {
                                best = score
                                if (best >= 0.92f) return true
                            }
                            x += strideX
                        }
                        y += strideY
                    }
                }
            }
        }
        return best >= 0.87f
    }

    private fun scorePatch(ref: FloatArray, patch: FloatArray): Float {
        if (ref.size != patch.size || ref.isEmpty()) return 0f
        var absErr = 0f
        var sqErr = 0f
        var edgeBonus = 0f
        for (i in ref.indices) {
            val d = ref[i] - patch[i]
            absErr += abs(d)
            sqErr += d * d
            if (abs(ref[i]) > 0.8f && abs(patch[i]) > 0.8f) edgeBonus += 1f
        }
        val n = ref.size.toFloat()
        val mae = absErr / n
        val rmse = kotlin.math.sqrt((sqErr / n).toDouble()).toFloat()
        val corr = normalizedCrossCorrelation(ref, patch)
        val edgeMatch = edgeBonus / n
        val s1 = 1f - minOf(1f, mae / 1.15f)
        val s2 = 1f - minOf(1f, rmse / 1.7f)
        return (corr * 0.56f) + (s1 * 0.22f) + (s2 * 0.12f) + (edgeMatch * 0.10f)
    }
}

data class RectFrac(val left: Float, val top: Float, val right: Float, val bottom: Float)
