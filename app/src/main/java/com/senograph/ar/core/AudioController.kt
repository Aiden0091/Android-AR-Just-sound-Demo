package com.senograph.ar.core

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class AudioController(context: Context) {
    private val player = ExoPlayer.Builder(context).build()

    val isPlaying: Boolean
        get() = player.isPlaying

    fun playFromStart(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        try {
            val uri = Uri.parse(uriString)
            val item = MediaItem.fromUri(uri)
            if (player.currentMediaItem?.localConfiguration?.uri != uri) {
                player.setMediaItem(item)
                player.prepare()
            }
            player.seekTo(0)
            player.playWhenReady = true
            player.play()
        } catch (_: Throwable) {
        }
    }

    fun stopAndRewind() {
        try {
            if (player.mediaItemCount > 0) {
                player.pause()
                player.seekTo(0)
            }
        } catch (_: Throwable) {
        }
    }

    fun release() {
        player.release()
    }
}
