package com.kurisu.musique

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Green = Color(0xFF1DB954)

@Composable
fun MusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Green,
            onPrimary = Color.Black,
            background = Color(0xFF121212),
            surface = Color(0xFF121212),
            onBackground = Color.White,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF282828),
            secondaryContainer = Color(0xFF2A2A2A)
        ),
        content = content
    )
}

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudioAccess(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, audioPermission()) == PackageManager.PERMISSION_GRANTED

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun AppRoot(state: PlayerState) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioAccess(context)) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasAudioAccess(context) }

    val perms = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }
    LaunchedEffect(granted) {
        if (granted) songs = withContext(Dispatchers.IO) { loadSongs(context) }
    }

    val store = remember { PlaylistStore(context) }
    var playlists by remember { mutableStateOf(store.load()) }
    fun savePlaylists(m: Map<String, List<Long>>) {
        playlists = m
        store.save(m)
    }

    val byId = remember(songs) { songs.associateBy { it.id } }
    var tab by remember { mutableIntStateOf(0) }
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var pickerSong by remember { mutableStateOf<Song?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(state)
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0; openPlaylist = null },
                        icon = { Icon(Icons.Filled.LibraryMusic, null) },
                        label = { Text("Titres") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1; openPlaylist = null },
                        icon = { Icon(Icons.Filled.PlaylistPlay, null) },
                        label = { Text("Playlists") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.QueueMusic, null) },
                        label = { Text("File") }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!granted) {
                Column(Modifier.padding(24.dp)) {
                    Text("L'accès à ta musique est nécessaire pour afficher tes morceaux.")
                    Button(onClick = { launcher.launch(perms) }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Autoriser")
                    }
                }
            } else when (tab) {
                0 -> LibraryScreen(songs, state) { pickerSong = it }
                1 -> {
                    val name = openPlaylist
                    val ids = name?.let { playlists[it] }
                    if (name != null && ids != null) {
                        BackHandler { openPlaylist = null }
                        PlaylistDetail(
                            name = name,
                            ids = ids,
                            byId = byId,
                            state = state,
                            onBack = { openPlaylist = null },
                            onChange = { savePlaylists(playlists + (name to it)) },
                            onDelete = { savePlaylists(playlists - name); openPlaylist = null }
                        )
                    } else {
                        PlaylistsScreen(playlists, onOpen = { openPlaylist = it }, onCreate = { showCreate = true })
                    }
                }
                else -> QueueScreen(state)
            }
        }
    }

    if (!showCreate) {
        pickerSong?.let { song ->
            AlertDialog(
                onDismissRequest = { pickerSong = null },
                title = { Text("Ajouter à une playlist") },
                text = {
                    Column {
                        playlists.keys.forEach { name ->
                            TextButton(onClick = {
                                savePlaylists(playlists + (name to (playlists[name].orEmpty() + song.id)))
                                pickerSong = null
                            }) { Text(name) }
                        }
                        TextButton(onClick = { showCreate = true }) { Text("+ Nouvelle playlist") }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { pickerSong = null }) { Text("Annuler") } }
            )
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Nouvelle playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nom") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && name.trim() !in playlists,
                    onClick = {
                        val n = name.trim()
                        savePlaylists(playlists + (n to listOfNotNull(pickerSong?.id)))
                        pickerSong = null
                        showCreate = false
                    }
                ) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Annuler") } }
        )
    }
}

@Composable
fun SongRow(song: Song, onPlay: () -> Unit, menu: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text("${song.artist} · ${fmt(song.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onPlay)
    )
}

@Composable
fun LibraryScreen(songs: List<Song>, state: PlayerState, onAddToPlaylist: (Song) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Rechercher un titre ou un artiste") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        if (songs.isEmpty()) Text("Aucun morceau trouvé.", Modifier.padding(16.dp))
        LazyColumn {
            itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    song = s,
                    onPlay = { state.playNow(shown, i) },
                    menu = listOf(
                        "Lire ensuite" to { state.playNext(s) },
                        "Ajouter à la fin de la file" to { state.addToEnd(s) },
                        "Ajouter à une playlist" to { onAddToPlaylist(s) }
                    )
                )
            }
        }
    }
}

@Composable
fun PlaylistsScreen(playlists: Map<String, List<Long>>, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Playlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = onCreate) {
                Icon(Icons.Filled.Add, null)
                Text("Nouvelle")
            }
        }
        if (playlists.isEmpty()) Text("Aucune playlist pour l'instant.", Modifier.padding(16.dp))
        LazyColumn {
            itemsIndexed(playlists.entries.toList()) { _, e ->
                ListItem(
                    headlineContent = { Text(e.key) },
                    supportingContent = { Text("${e.value.size} morceau(x)") },
                    modifier = Modifier.clickable { onOpen(e.key) }
                )
            }
        }
    }
}

@Composable
fun PlaylistDetail(
    name: String,
    ids: List<Long>,
    byId: Map<Long, Song>,
    state: PlayerState,
    onBack: () -> Unit,
    onChange: (List<Long>) -> Unit,
    onDelete: () -> Unit
) {
    val rows = ids.mapIndexedNotNull { idx, id -> byId[id]?.let { idx to it } }
    val songs = rows.map { it.second }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Retour") }
            Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Supprimer la playlist") }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Button(onClick = { if (songs.isNotEmpty()) state.playNow(songs, 0) }, enabled = songs.isNotEmpty()) {
                Icon(Icons.Filled.PlayArrow, null)
                Text("Lire")
            }
            OutlinedButton(
                onClick = { state.addAllToEnd(songs) },
                enabled = songs.isNotEmpty(),
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("Ajouter à la file") }
        }
        if (songs.isEmpty()) Text("Playlist vide. Ajoute des titres depuis l'onglet Titres.", Modifier.padding(16.dp))
        LazyColumn {
            itemsIndexed(rows) { pos, (idx, s) ->
                SongRow(
                    song = s,
                    onPlay = { state.playNow(songs, pos) },
                    menu = listOf(
                        "Lire ensuite" to { state.playNext(s) },
                        "Ajouter à la fin de la file" to { state.addToEnd(s) },
                        "Retirer de la playlist" to {
                            onChange(ids.toMutableList().also { it.removeAt(idx) })
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun QueueScreen(state: PlayerState) {
    val q = state.queue
    val cur = state.currentIndex
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("File d'attente · ${q.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { state.clear() }, enabled = q.isNotEmpty()) { Text("Vider") }
        }
        if (q.isEmpty()) Text("La file est vide. Ajoute des morceaux depuis l'onglet Titres.", Modifier.padding(16.dp))
        LazyColumn {
            itemsIndexed(q) { i, item -> QueueRow(state, item, i, cur, q.lastIndex) }
        }
    }
}

@Composable
private fun QueueRow(state: PlayerState, item: MediaItem, i: Int, cur: Int, last: Int) {
    var open by remember { mutableStateOf(false) }
    val md = item.mediaMetadata
    val isCurrent = i == cur
    ListItem(
        colors = ListItemDefaults.colors(containerColor = if (isCurrent) Color(0xFF1F3D2A) else Color.Transparent),
        headlineContent = {
            Text(
                md.title?.toString() ?: "Sans titre",
                color = if (isCurrent) Green else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = { Text(md.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = i > 0, onClick = { state.move(i, i - 1) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Monter")
                }
                IconButton(enabled = i < last, onClick = { state.move(i, i + 1) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Descendre")
                }
                Box {
                    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(text = { Text("Lire ensuite") }, onClick = { open = false; state.moveToPlayNext(i) })
                        DropdownMenuItem(text = { Text("Mettre en fin de file") }, onClick = { open = false; state.moveToEnd(i) })
                        DropdownMenuItem(text = { Text("Retirer de la file") }, onClick = { open = false; state.remove(i) })
                    }
                }
            }
        },
        modifier = Modifier.clickable { state.jumpTo(i) }
    )
}

@Composable
fun MiniPlayer(state: PlayerState) {
    val item = state.queue.getOrNull(state.currentIndex) ?: return
    Surface(color = Color(0xFF282828), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.mediaMetadata.title?.toString() ?: "Sans titre", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.mediaMetadata.artist?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { state.previous() }) { Icon(Icons.Filled.SkipPrevious, "Précédent") }
            IconButton(onClick = { state.togglePlay() }) {
                Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Lecture / pause")
            }
            IconButton(onClick = { state.next() }) { Icon(Icons.Filled.SkipNext, "Suivant") }
        }
    }
}
