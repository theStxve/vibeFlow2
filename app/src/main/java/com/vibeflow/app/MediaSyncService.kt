package com.vibeflow.app

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.palette.graphics.Palette

class MediaSyncService : NotificationListenerService() {

    private var lastExtractedBitmap: Bitmap? = null
    private lateinit var mediaSessionManager: MediaSessionManager
    private val activeControllers = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateControllers(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            val componentName = ComponentName(this, MediaSyncService::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            updateControllers(mediaSessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (e: Exception) {}
        clearAllCallbacks()
    }

    private fun clearAllCallbacks() {
        for ((controller, callback) in activeControllers) {
            try {
                controller.unregisterCallback(callback)
            } catch (e: Exception) {}
        }
        activeControllers.clear()
    }

    private fun updateControllers(controllers: List<MediaController>?) {
        clearAllCallbacks()
        if (controllers == null) return

        for (controller in controllers) {
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    checkMetadata(controller)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (state?.state == PlaybackState.STATE_PLAYING) {
                        checkMetadata(controller)
                    }
                }
            }
            try {
                controller.registerCallback(callback)
                activeControllers[controller] = callback
            } catch (e: Exception) {}
        }
        checkActivePlayingSession()
    }

    private fun checkActivePlayingSession() {
        val prefs = getSharedPreferences("vibeflow_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("smart_sync_spotify", true)) return

        for (controller in activeControllers.keys) {
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                checkMetadata(controller)
                return
            }
        }
    }

    private fun checkMetadata(controller: MediaController) {
        val prefs = getSharedPreferences("vibeflow_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("smart_sync_spotify", true)) return

        val metadata = controller.metadata
        if (metadata != null) {
            val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART) 
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            
            if (bitmap != null && bitmap != lastExtractedBitmap) {
                lastExtractedBitmap = bitmap
                extractColorsFromBitmap(bitmap, prefs)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun extractColorsFromBitmap(bitmap: Bitmap, prefs: android.content.SharedPreferences) {
        Palette.from(bitmap).generate { palette ->
            if (palette != null) {
                val colorA = palette.getVibrantColor(palette.getDominantColor(0xFF141E30.toInt()))
                val colorB = palette.getMutedColor(palette.getDarkVibrantColor(0xFF243B55.toInt()))

                prefs.edit()
                    .putInt("spotify_color_a", colorA)
                    .putInt("spotify_color_b", colorB)
                    .putBoolean("is_spotify_active", true)
                    .apply()
            }
        }
    }
}
