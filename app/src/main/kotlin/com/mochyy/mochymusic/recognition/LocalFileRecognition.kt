package com.mochyy.mochymusic.recognition

import android.content.Context
import android.media.AudioFormat
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import com.music.shazamkit.Shazam
import com.music.shazamkit.models.RecognitionResult
import com.mochyy.mochymusic.playback.audio.StreamPcmDecoder
import com.mochyy.mochymusic.utils.OfflineMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream

/** Reads a bounded sample locally; only its fingerprint is sent to the recognition provider. */
@androidx.annotation.OptIn(UnstableApi::class)
object LocalFileRecognition {
    suspend fun recognize(context: Context, uri: Uri, durationSeconds: Int): RecognitionResult = withContext(Dispatchers.IO) {
        check(!OfflineMode.isOn) { "offline" }
        require(uri.scheme == "content" || uri.scheme == "file") { "local_file_required" }
        withTimeout(75_000L) {
            val startUs = if (durationSeconds >= 22) 5_000_000L else 0L
            val endUs = startUs + 12_000_000L
            val output = ByteArrayOutputStream()
            var rate = 0
            var frames = 0
            StreamPcmDecoder.decode(DefaultDataSource.Factory(context), uri, "recognition-local", endUs) { pcm, channels, sampleRate, timeUs ->
                require(sampleRate in 8000..384000 && channels in 1..32) { "unsupported_format" }
                if (rate != 0) require(rate == sampleRate) { "changing_sample_rate" }
                rate = sampleRate
                var frameTime = timeUs
                var frameIndex = 0L
                while (pcm.remaining() >= channels) {
                    val mono = RecognitionPcm.mono(pcm, channels)
                    frameTime = timeUs + frameIndex++ * 1_000_000L / sampleRate
                    if (frameTime >= startUs && frameTime < endUs && frames < sampleRate * 12) {
                        output.write(mono.toInt() and 255)
                        output.write((mono.toInt() shr 8) and 255)
                        frames++
                    }
                }
            }
            require(rate > 0 && frames >= rate * 3) { "sample_too_short" }
            coroutineContext.ensureActive()
            val resampled = AudioResampler.resample(
                DecodedAudio(output.toByteArray(), 1, rate, AudioFormat.ENCODING_PCM_16BIT),
                VibraSignature.REQUIRED_SAMPLE_RATE,
            ).getOrThrow()
            check(!OfflineMode.isOn) { "offline" }
            val signature = VibraSignature.fromI16(resampled.data)
            coroutineContext.ensureActive()
            val result = Shazam.recognize(signature, resampled.data.size / 2 * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE).getOrThrow()
            require(result.title.isNotBlank() && result.artist.isNotBlank()) { "incomplete_match" }
            result
        }
    }
}
