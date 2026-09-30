package com.kurisu.musique

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    fun toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
}

fun loadSongs(context: Context): List<Song> {
    val result = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (c.moveToNext()) {
            val artist = c.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Artiste inconnu"
            result.add(
                Song(
                    id = c.getLong(idCol),
                    title = c.getString(titleCol) ?: "Sans titre",
                    artist = artist,
                    album = c.getString(albumCol) ?: "",
                    durationMs = c.getLong(durCol)
                )
            )
        }
    }
    return result
}

class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(): Map<String, List<Long>> {
        val raw = prefs.getString("data", "{}") ?: "{}"
        val obj = JSONObject(raw)
        val out = LinkedHashMap<String, List<Long>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr = obj.getJSONArray(k)
            out[k] = (0 until arr.length()).map { arr.getLong(it) }
        }
        return out
    }

    fun save(data: Map<String, List<Long>>) {
        val obj = JSONObject()
        for ((name, ids) in data) {
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            obj.put(name, arr)
        }
        prefs.edit().putString("data", obj.toString()).apply()
    }
}
