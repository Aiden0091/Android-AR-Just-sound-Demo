package com.senograph.ar.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class GrayImage(
    val width: Int,
    val height: Int,
    val values: FloatArray
) {
    fun resize(newW: Int, newH: Int): GrayImage {
        val out = FloatArray(newW * newH)
        for (y in 0 until newH) {
            val srcY = ((y.toFloat() / newH) * (height - 1)).roundToInt().coerceIn(0, height - 1)
            for (x in 0 until newW) {
                val srcX = ((x.toFloat() / newW) * (width - 1)).roundToInt().coerceIn(0, width - 1)
                out[y * newW + x] = values[srcY * width + srcX]
            }
        }
        return GrayImage(newW, newH, out)
    }

    fun rotate(degrees: Int): GrayImage {
        if (degrees == 0) return this
        val rad = Math.toRadians(degrees.toDouble())
        val cos = kotlin.math.cos(rad)
        val sin = kotlin.math.sin(rad)
        val out = FloatArray(width * height)
        val cx = (width - 1) / 2.0
        val cy = (height - 1) / 2.0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                val srcX = (dx * cos + dy * sin + cx).roundToInt()
                val srcY = (-dx * sin + dy * cos + cy).roundToInt()
                out[y * width + x] = if (srcX in 0 until width && srcY in 0 until height) values[srcY * width + srcX] else 0f
            }
        }
        return GrayImage(width, height, out)
    }

    fun extract(startX: Int, startY: Int, w: Int, h: Int): GrayImage {
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val srcRow = (startY + y) * width
            val dstRow = y * w
            for (x in 0 until w) out[dstRow + x] = values[srcRow + startX + x]
        }
        return GrayImage(w, h, out)
    }

    fun histogramNormalize(): GrayImage {
        val out = values.copyOf()
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for (v in out) {
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }
        val range = max(1e-6f, maxV - minV)
        for (i in out.indices) out[i] = ((out[i] - minV) / range) * 2f - 1f
        return GrayImage(width, height, out)
    }

    fun gaussianBlur3x3(): GrayImage {
        if (width < 3 || height < 3) return this
        val out = FloatArray(width * height)
        val k = arrayOf(
            floatArrayOf(1f, 2f, 1f),
            floatArrayOf(2f, 4f, 2f),
            floatArrayOf(1f, 2f, 1f)
        )
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sum = 0f
                var w = 0f
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val kw = k[ky + 1][kx + 1]
                        sum += values[(y + ky) * width + (x + kx)] * kw
                        w += kw
                    }
                }
                out[y * width + x] = sum / w
            }
        }
        return GrayImage(width, height, out)
    }

    fun sobelMagnitude(): GrayImage {
        if (width < 3 || height < 3) return this
        val out = FloatArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val gx =
                    (-values[(y - 1) * width + (x - 1)]) + values[(y - 1) * width + (x + 1)] +
                    (-2 * values[y * width + (x - 1)]) + (2 * values[y * width + (x + 1)]) +
                    (-values[(y + 1) * width + (x - 1)]) + values[(y + 1) * width + (x + 1)]
                val gy =
                    (-values[(y - 1) * width + (x - 1)]) + (-2 * values[(y - 1) * width + x]) + (-values[(y - 1) * width + (x + 1)]) +
                    values[(y + 1) * width + (x - 1)] + (2 * values[(y + 1) * width + x]) + values[(y + 1) * width + (x + 1)]
                out[y * width + x] = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toFloat()
            }
        }
        return GrayImage(width, height, out)
    }

    fun sharpenLight(): GrayImage {
        val blurred = gaussianBlur3x3()
        val out = FloatArray(values.size)
        for (i in values.indices) out[i] = (values[i] + (values[i] - blurred.values[i]) * 0.35f).coerceIn(-2f, 2f)
        return GrayImage(width, height, out)
    }

    companion object {
        fun fromBitmap(bitmap: Bitmap, width: Int, height: Int): GrayImage {
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            val values = FloatArray(width * height)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                values[i] = (0.299f * r) + (0.587f * g) + (0.114f * b)
            }
            if (scaled !== bitmap) scaled.recycle()
            return GrayImage(width, height, values)
        }
    }
}

fun normalizedCrossCorrelation(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size || a.isEmpty()) return 0f
    var sumA = 0f
    var sumB = 0f
    for (i in a.indices) {
        sumA += a[i]
        sumB += b[i]
    }
    val meanA = sumA / a.size
    val meanB = sumB / b.size
    var numerator = 0f
    var denomA = 0f
    var denomB = 0f
    for (i in a.indices) {
        val da = a[i] - meanA
        val db = b[i] - meanB
        numerator += da * db
        denomA += da * da
        denomB += db * db
    }
    val denom = kotlin.math.sqrt((denomA * denomB).toDouble()).toFloat()
    return if (denom <= 1e-6f) 0f else (numerator / denom).coerceIn(-1f, 1f)
}

fun bitmapFromImageProxy(image: ImageProxy, targetWidth: Int = 320, targetHeight: Int = 240): GrayImage {
    val yPlane = image.planes[0]
    val buffer = yPlane.buffer.duplicate()
    val rowStride = yPlane.rowStride
    val pixelStride = yPlane.pixelStride

    val out = FloatArray(targetWidth * targetHeight)
    val srcW = image.width
    val srcH = image.height
    for (ty in 0 until targetHeight) {
        val sy = (ty.toFloat() / targetHeight * (srcH - 1)).roundToInt().coerceIn(0, srcH - 1)
        val rowOffset = sy * rowStride
        for (tx in 0 until targetWidth) {
            val sx = (tx.toFloat() / targetWidth * (srcW - 1)).roundToInt().coerceIn(0, srcW - 1)
            val idx = rowOffset + sx * pixelStride
            out[ty * targetWidth + tx] = (buffer.get(idx).toInt() and 0xFF).toFloat()
        }
    }
    return GrayImage(targetWidth, targetHeight, out)
}

fun bitmapToGray(bitmap: Bitmap, width: Int, height: Int): GrayImage {
    return GrayImage.fromBitmap(bitmap, width, height)
}

fun ensureArgb(bitmap: Bitmap): Bitmap {
    if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
    val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawBitmap(bitmap, 0f, 0f, null)
    return out
}
