package com.senograph.ar.core

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.ArrayDeque
import android.os.SystemClock

class UltraFrameAnalyzer(
    private val detector: UltraTemplateDetector,
    private val onVisibleChanged: (Boolean) -> Unit
) : ImageAnalysis.Analyzer {
    @Volatile var enabled: Boolean = true

    private val history = ArrayDeque<Boolean>(9)
    private var stableVisible = false
    private var lastDispatchAt = 0L

    override fun analyze(image: ImageProxy) {
        if (!enabled) {
            image.close()
            return
        }
        try {
            val gray = bitmapFromImageProxy(image, 240, 180)
            val matched = detector.matches(gray)
            if (history.size == 9) history.removeFirst()
            history.addLast(matched)

            val positive = history.count { it } >= 6
            val now = SystemClock.elapsedRealtime()
            if (positive != stableVisible && now - lastDispatchAt > 160) {
                stableVisible = positive
                lastDispatchAt = now
                onVisibleChanged(positive)
            }
        } catch (_: Throwable) {
        } finally {
            image.close()
        }
    }
}
