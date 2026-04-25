package com.carradio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.carradio.R
import com.carradio.data.model.Station
import com.carradio.ui.main.MainActivity

/**
 * Foreground service for radio playback
 * Supports background playback and media controls
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RadioService : MediaSessionService() {

    inner class LocalBinder : Binder() {
        fun getService(): RadioService = this@RadioService
    }

    private val binder = LocalBinder()

    companion object {
        const val CHANNEL_ID = "radio_service_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.carradio.ACTION_STOP"
        const val ACTION_STATION_CHANGED = "com.carradio.STATION_CHANGED"
    }

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var currentStation: Station? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Add player event listener for debugging
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> Log.d("RadioService", "Player state: IDLE")
                    Player.STATE_BUFFERING -> Log.d("RadioService", "Player state: BUFFERING")
                    Player.STATE_READY -> Log.d("RadioService", "Player state: READY, duration=${exoPlayer?.duration}")
                    Player.STATE_ENDED -> Log.d("RadioService", "Player state: ENDED")
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                Log.d("RadioService", "playWhenReady: $playWhenReady, reason: $reason")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("RadioService", "Player error: errorCode=${error.errorCode}, message=${error.message}", error)
                error.cause?.let {
                    Log.e("RadioService", "Player error cause: ${it.javaClass.simpleName}: ${it.message}", it)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("RadioService", "isPlaying changed to: $isPlaying, audioSessionId=${exoPlayer?.audioSessionId}")
            }
        })

        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setSessionActivity(getSessionActivityPendingIntent())
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun getSessionActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateNotification(station: Station) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(station.title)
            .setContentText(station.getDisplaySubtitle())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(getSessionActivityPendingIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_notification_stop,
                getString(R.string.stop),
                getStopActionPendingIntent()
            )

        val notification = builder.build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun getStopActionPendingIntent(): PendingIntent {
        val intent = Intent(this, RadioService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    fun playStation(station: Station) {
        currentStation = station
        val playUrl = station.getBestPlayUrl()
        Log.d("RadioService", "Playing station: ${station.title}, URL: $playUrl")
        Log.d("RadioService", "Station detail urls: mp3PlayUrlLow=${station.mp3PlayUrlLow}, playUrlLow=${station.playUrlLow}, playUrlMulti=${station.playUrlMulti}")

        playUrl?.let { url ->
            Log.d("RadioService", "Attempting to play URL: $url")
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaId(station.contentId)
                .build()
            exoPlayer?.let { player ->
                Log.d("RadioService", "Calling exoPlayer setMediaItem & prepare")
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
                Log.d("RadioService", "Called play() for station: ${station.title}")
            } ?: Log.e("RadioService", "ExoPlayer is NULL when trying to play")
            updateNotification(station)

            // Send broadcast to notify station change
            val intent = Intent(ACTION_STATION_CHANGED).apply {
                putExtra("station", station)
            }
            sendBroadcast(intent)
        } ?: run {
            Log.e("RadioService", "No playable URL found for station: ${station.title}")
        }
    }

    fun pausePlayback() { exoPlayer?.pause() }
    fun resumePlayback() { exoPlayer?.play() }

    fun stopPlayback() {
        exoPlayer?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        currentStation = null
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false
    fun getCurrentStation(): Station? = currentStation

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
            }
            "com.carradio.ACTION_PLAY_STATION" -> {
                val stationId = intent.getStringExtra("station_id")
                val stationTitle = intent.getStringExtra("station_title")
                val stationSubtitle = intent.getStringExtra("station_subtitle")
                val stationImageUrl = intent.getStringExtra("station_image_url")
                val stationPlayUrl = intent.getStringExtra("station_play_url")

                if (stationId != null && stationTitle != null && !stationPlayUrl.isNullOrBlank()) {
                    val station = Station(
                        contentId = stationId,
                        title = stationTitle,
                        subtitle = stationSubtitle ?: "",
                        imageUrl = stationImageUrl,
                        playUrlLow = stationPlayUrl,
                        mp3PlayUrlLow = stationPlayUrl,  // Also set mp3 URL as fallback
                        playUrlMulti = stationPlayUrl      // Also set multi URL as fallback
                    )
                    playStation(station)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        exoPlayer?.release()
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }
}