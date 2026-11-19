package com.wigters.app.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.glance.appwidget.updateAll
import com.wigters.app.widget.DashboardWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

data class Media(val title: String, val artist: String, val playing: Boolean, val packageName: String?, val art: Bitmap?)

/** Música tocando agora, lida das sessões de mídia (precisa do acesso a notificações). */
object NowPlaying {
    private const val PREFS = "media"

    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(ComponentName(context, MediaListenerService::class.java).flattenToString())
    }

    fun read(context: Context): Media? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = p.getString("title", null) ?: return null
        val art = File(context.filesDir, "art.png").takeIf { it.exists() }
            ?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
        return Media(title, p.getString("artist", "") ?: "", p.getBoolean("playing", false), p.getString("pkg", null), art)
    }

    fun save(context: Context, c: MediaController?) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || title.isNullOrBlank()) {
            p.edit().clear().apply()
            File(context.filesDir, "art.png").delete()
            return
        }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val key = "$title|$artist"
        if (p.getString("key", null) != key) {
            val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            val file = File(context.filesDir, "art.png")
            if (art != null) {
                runCatching {
                    val small = Bitmap.createScaledBitmap(art, 160, 160, true)
                    file.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            } else file.delete()
        }
        p.edit()
            .putString("key", key)
            .putString("title", title)
            .putString("artist", artist)
            .putBoolean("playing", c.playbackState?.state == PlaybackState.STATE_PLAYING)
            .putString("pkg", c.packageName)
            .apply()
    }

    fun controllers(context: Context): List<MediaController> = runCatching {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaListenerService::class.java))
    }.getOrDefault(emptyList())

    fun pick(list: List<MediaController>?): MediaController? =
        list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
}

/** Serviço que o sistema mantém ativo quando o acesso a notificações está liberado. */
class MediaListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controller: MediaController? = null
    private var pushJob: Job? = null
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = push()
        override fun onMetadataChanged(metadata: MediaMetadata?) = push()
        override fun onSessionDestroyed() {
            attach(NowPlaying.pick(NowPlaying.controllers(this@MediaListenerService)))
            push()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(NowPlaying.pick(list))
        push()
    }

    override fun onListenerConnected() {
        runCatching {
            getSystemService(MediaSessionManager::class.java).addOnActiveSessionsChangedListener(
                sessionsListener, ComponentName(this, MediaListenerService::class.java), main
            )
        }
        attach(NowPlaying.pick(NowPlaying.controllers(this)))
        push()
    }

    override fun onListenerDisconnected() {
        runCatching { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener) }
        attach(null)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun attach(c: MediaController?) {
        if (c?.sessionToken == controller?.sessionToken && c != null) return
        controller?.unregisterCallback(callback)
        controller = c
        c?.registerCallback(callback, main)
    }

    private fun push() {
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(300)
            NowPlaying.save(this@MediaListenerService, controller)
            DashboardWidget().updateAll(this@MediaListenerService)
        }
    }
}
