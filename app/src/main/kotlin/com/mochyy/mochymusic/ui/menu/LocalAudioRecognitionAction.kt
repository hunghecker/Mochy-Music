package com.mochyy.mochymusic.ui.menu

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.music.shazamkit.models.RecognitionResult
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.recognition.LocalFileRecognition
import com.mochyy.mochymusic.ui.component.TopToaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/** Explicit network action: preview first, persist only after the user accepts the match. */
@Composable
internal fun LocalAudioRecognitionAction(song: Song) {
    if (!song.song.isLocal || song.song.localUri.isNullOrBlank()) return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    var open by remember(song.id) { mutableStateOf(false) }
    var busy by remember(song.id) { mutableStateOf(false) }
    var saving by remember(song.id) { mutableStateOf(false) }
    var result by remember(song.id) { mutableStateOf<RecognitionResult?>(null) }
    var error by remember(song.id) { mutableStateOf(false) }
    TextButton(onClick = { open = true; result = null; error = false }, enabled = !busy && !saving) {
        Text(stringResource(R.string.local_recognize_action))
    }
    if (open) AlertDialog(
        onDismissRequest = { if (!busy && !saving) open = false },
        title = { Text(stringResource(R.string.local_recognize_action)) },
        text = {
            val match = result
            Text(when {
                saving -> stringResource(R.string.local_recognize_saving)
                busy -> stringResource(R.string.local_recognize_busy)
                error -> stringResource(R.string.local_recognize_error)
                match != null -> stringResource(R.string.local_recognize_preview, song.title, song.artists.joinToString { it.name }, match.title, match.artist)
                else -> stringResource(R.string.local_recognize_consent)
            })
        },
        confirmButton = {
            if (!busy && !saving) TextButton(onClick = {
                val match = result
                if (match == null) {
                    busy = true; error = false
                    scope.launch {
                        try { result = LocalFileRecognition.recognize(context, android.net.Uri.parse(song.song.localUri), song.song.duration) }
                        catch (_: kotlinx.coroutines.TimeoutCancellationException) { error = true }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = true }
                        finally { busy = false }
                    }
                } else {
                    saving = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                // Fetch current state so playback counts, likes and edits made while matching survive.
                                val latest = database.song(song.id).first() ?: throw IllegalStateException("song_removed")
                                check(latest.song.isLocal)
                                val artist = database.artistByName(match.artist) ?: ArtistEntity(
                                    id = ArtistEntity.generateArtistId(), name = match.artist, isLocal = true,
                                )
                                database.repairLocalMetadataText(
                                    latest.song.copy(title = match.title), listOf(artist), database.songArtistMap(song.id), null,
                                )
                            }
                            open = false
                            TopToaster.show(context.getString(R.string.local_recognize_saved), iconRes = R.drawable.library_music)
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { result = null; error = true }
                        finally { saving = false }
                    }
                }
            }) { Text(stringResource(if (result != null) R.string.local_recognize_apply else R.string.local_recognize_start)) }
        },
        dismissButton = { TextButton(onClick = { open = false }, enabled = !busy && !saving) { Text(stringResource(android.R.string.cancel)) } },
    )
}
