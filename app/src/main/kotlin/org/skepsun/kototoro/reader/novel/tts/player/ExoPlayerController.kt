package org.skepsun.kototoro.reader.novel.tts.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.skepsun.kototoro.reader.novel.tts.model.AudioData

class ExoPlayerController(context: Context) {

    private val _currentItemIndex = MutableStateFlow<Int?>(null)
    val currentItemIndex = _currentItemIndex.asStateFlow()

    private val player = ExoPlayer.Builder(context).build().apply {
        // 禁止 Item 之间自动停顿，确保无缝衔接
        pauseAtEndOfMediaItems = false
        addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.toIntOrNull()?.let {
                    _currentItemIndex.value = it
                }
                if (mediaItem == null) {
                    _currentItemIndex.value = null
                }
            }
        })
    }

    fun play(audioFlow: Flow<Pair<Int, AudioData>>) {
        CoroutineScope(Dispatchers.Main).launch {
            audioFlow.collect { (index, audio) ->
                val item = MediaItem.Builder()
                    .setUri(audio.uri)
                    .setMediaId(index.toString())
                    .build()

                player.addMediaItem(item)

                if (player.mediaItemCount == 1) {
                    player.prepare()
                }

                if (!player.isPlaying && player.playbackState != ExoPlayer.STATE_BUFFERING) {
                    player.play()
                }

                trimQueueIfNeeded()
            }
        }
    }

    private fun trimQueueIfNeeded() {
        val max = 30
        if (player.mediaItemCount > max) {
            // Remove the earliest item that has already been played.
            // Exoplayer's media queue runs sequentially. Removing from 0 while playing further items
            // forces older items to be dropped to prevent OOM.
            if (player.currentMediaItemIndex > 0) {
                player.removeMediaItem(0)
            }
        }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }
    
    fun pause() {
        player.pause()
    }
    
    fun resume() {
        player.play()
    }
    
    fun seekNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }
    
    fun seekPrev() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0)
        }
    }
    
    fun release() {
        player.release()
    }
}
