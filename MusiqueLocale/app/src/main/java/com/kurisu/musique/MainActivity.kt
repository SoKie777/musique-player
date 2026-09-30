package com.kurisu.musique

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** État du lecteur, miroir de la file d'attente du MediaController. */
class PlayerState : Player.Listener {
    private var controller: MediaController? = null

    var queue by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    fun attach(c: MediaController) {
        controller = c
        c.addListener(this)
        refresh()
    }

    fun detach() {
        controller?.removeListener(this)
        controller = null
    }

    private fun refresh() {
        val c = controller ?: return
        queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        currentIndex = if (c.mediaItemCount == 0) -1 else c.currentMediaItemIndex
        isPlaying = c.isPlaying
    }

    override fun onEvents(player: Player, events: Player.Events) = refresh()

    // ---- Lecture ----
    fun playNow(songs: List<Song>, start: Int) {
        val c = controller ?: return
        c.setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }

    // ---- File d'attente ----
    /** Ajoute un morceau juste après le morceau en cours. */
    fun playNext(song: Song) {
        val c = controller ?: return
        val index = if (c.mediaItemCount == 0) 0 else c.currentMediaItemIndex + 1
        c.addMediaItem(index, song.toMediaItem())
        c.prepare()
    }

    /** Ajoute un morceau en fin de file. */
    fun addToEnd(song: Song) {
        val c = controller ?: return
        c.addMediaItem(song.toMediaItem())
        c.prepare()
    }

    fun addAllToEnd(songs: List<Song>) {
        val c = controller ?: return
        c.addMediaItems(songs.map { it.toMediaItem() })
        c.prepare()
    }

    fun remove(index: Int) { controller?.removeMediaItem(index) }
    fun move(from: Int, to: Int) { controller?.moveMediaItem(from, to) }

    /** Place le morceau à l'index donné juste après le morceau en cours. */
    fun moveToPlayNext(index: Int) {
        val c = controller ?: return
        val cur = c.currentMediaItemIndex
        if (index == cur) return
        val target = if (index > cur) cur + 1 else cur
        c.moveMediaItem(index, target)
    }

    fun moveToEnd(index: Int) {
        val c = controller ?: return
        c.moveMediaItem(index, c.mediaItemCount - 1)
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L)
        c.prepare()
        c.play()
    }

    fun clear() { controller?.clearMediaItems() }
}

class MainActivity : ComponentActivity() {
    private val state = PlayerState()
    private var future: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicTheme { AppRoot(state) }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        future = f
        f.addListener({
            try {
                state.attach(f.get())
            } catch (e: Exception) {
                // contrôleur déjà libéré
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        state.detach()
        future?.let { MediaController.releaseFuture(it) }
        future = null
        super.onStop()
    }
}
