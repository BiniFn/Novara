package org.skepsun.kototoro.reader.novel.tts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import org.skepsun.kototoro.R
import org.skepsun.kototoro.reader.novel.tts.model.Token

@AndroidEntryPoint
class TtsService : LifecycleService() {

    // Note: Assuming TtsManager is provided by Hilt or manually instantiated for now
    // For MVVM, this could be passed via binder or injected. We use late init placeholder.
    lateinit var ttsManager: TtsManager

    private val binder = TtsBinder()

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    override fun onCreate() {
        super.onCreate()
        
        val ttsEngine = org.skepsun.kototoro.reader.novel.tts.engine.SystemTTSEngine(
            android.speech.tts.TextToSpeech(this) { _ -> },
            this
        )
        val cache = org.skepsun.kototoro.reader.novel.tts.TtsCache(this)
        val player = org.skepsun.kototoro.reader.novel.tts.player.ExoPlayerController(this)
        ttsManager = TtsManager(this, ttsEngine, cache, player)
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        if (::ttsManager.isInitialized) {
            ttsManager.release()
        }
        super.onDestroy()
    }

    // Public API for binding components
    fun startTts(tokens: List<Token>, startIndex: Int) {
        ttsManager.start(tokens, startIndex)
    }

    fun pause() {
        ttsManager.pause()
    }

    fun resume() {
        ttsManager.resume()
    }
    
    fun seekNext() {
        ttsManager.seekNext()
    }
    
    fun seekPrev() {
        ttsManager.seekPrev()
    }

    fun stopTts() {
        ttsManager.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun getState(): StateFlow<TtsState> = ttsManager.state
    
    fun getPlayingTokenIndex() = ttsManager.currentPlayingTokenIndex
    
    fun getToken(index: Int) = ttsManager.getToken(index)

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Kototoro ")
        .setContentText("朗读中...")
        .setSmallIcon(R.mipmap.ic_launcher)
        // Add pending intent to return to Reader UI
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TTS Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "kototoro_tts_channel"
        private const val NOTIFICATION_ID = 20000
    }
}
