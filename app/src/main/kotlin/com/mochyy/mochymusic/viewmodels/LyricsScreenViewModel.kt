// LyricsScreenViewModel.kt

package com.mochyy.mochymusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.lyrics.LyricsHelper
import com.mochyy.mochymusic.models.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricsScreenViewModel
@Inject
constructor(
    private val lyricsHelper: LyricsHelper,
    // Exposed so the lyrics screen can hand it to LyricsTranslationHelper, which persists a
    // finished translation against the song row.
    val database: MusicDatabase,
) : ViewModel() {
    private val requestedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _completedIds = MutableStateFlow<Set<String>>(emptySet())
    val completedIds = _completedIds.asStateFlow()

    fun ensureLyricsLoaded(mediaMetadata: MediaMetadata) {
        val id = mediaMetadata.id
        if (!requestedIds.add(id)) return
        _completedIds.update { it - id }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = database.lyrics(id).first()
                // Re-read local files even when a previous lookup stored "not found" or an
                // online source. Imports made by older builds must work without re-importing.
                val embedded = lyricsHelper.getEmbeddedLyrics(mediaMetadata)
                if (embedded != null && (existing?.lyrics != embedded.lyrics || existing.provider != embedded.provider)) {
                    database.upsert(LyricsEntity(id = id, lyrics = embedded.lyrics, provider = embedded.provider))
                    return@launch
                }
                // YouLyPlus and Paxsenix used to join syllables with spaces ("wa nt yo u"). Their
                // converters are fixed, but lyrics cached before that keep the split text forever, so
                // a cached copy from either that still reads as syllable-split is fetched again once.
                val staleSyllableSplit = existing != null &&
                    existing.provider in SyllableFixedProviders &&
                    com.mochyy.mochymusic.lyrics.LyricsUtils.isSyllableSplit(existing.lyrics)
                if (existing != null && !staleSyllableSplit) return@launch

                val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                // nothing was asked: not a "not found" to keep
                if (lyricsWithProvider.provider == com.mochyy.mochymusic.lyrics.LyricsHelper.NoConnectionProvider) return@launch
                database.upsert(
                    LyricsEntity(
                        id = id,
                        lyrics = lyricsWithProvider.lyrics,
                        provider = lyricsWithProvider.provider,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.mochyy.mochymusic.utils.reportException(e)
            } finally {
                _completedIds.update { it + id }
                // A lookup skipped offline or interrupted may be retried on reopening.
                requestedIds.remove(id)
            }
        }
    }
}

private val SyllableFixedProviders = setOf("YouLyPlus", "Paxsenix")
