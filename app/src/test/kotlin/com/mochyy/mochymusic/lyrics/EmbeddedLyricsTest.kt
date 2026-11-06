package com.mochyy.mochymusic.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmbeddedLyricsTest {
    private val lyrics = "[00:01.00]Như ngày hôm qua\n[00:03.00]Lời trong tệp"

    private fun find(bytes: ByteArray): String? = EmbeddedLyrics.find(object : EmbeddedLyrics.Source {
        override val size = bytes.size.toLong()
        override fun read(position: Long, length: Int): ByteArray =
            if (position < 0 || position >= size || length <= 0) byteArrayOf()
            else bytes.copyOfRange(position.toInt(), minOf(size, position + length).toInt())
    })

    private fun int(value: Int, order: ByteOrder = ByteOrder.BIG_ENDIAN) =
        ByteBuffer.allocate(4).order(order).putInt(value).array()

    private fun syncsafe(value: Int) = byteArrayOf(
        ((value shr 21) and 127).toByte(), ((value shr 14) and 127).toByte(),
        ((value shr 7) and 127).toByte(), (value and 127).toByte(),
    )

    private fun id3(version: Int, vararg bodies: Pair<String, ByteArray>): ByteArray {
        val frames = bodies.fold(byteArrayOf()) { result, (id, body) ->
            result + id.toByteArray() + when (version) {
                2 -> int(body.size).copyOfRange(1, 4)
                4 -> syncsafe(body.size) + byteArrayOf(0, 0)
                else -> int(body.size) + byteArrayOf(0, 0)
            } + body
        }
        return "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, 0) + syncsafe(frames.size) + frames
    }

    private fun uslt(text: String) = byteArrayOf(3) + "vie".toByteArray() + byteArrayOf(0) + text.toByteArray()

    @Test fun readsUtf8Mp3AcrossId3Versions() {
        for (version in 2..4) {
            assertEquals(lyrics, find(id3(version, (if (version == 2) "ULT" else "USLT") to uslt(lyrics))))
        }
    }

    @Test fun readsUtf16Mp3() {
        val body = byteArrayOf(1) + "vie".toByteArray() + byteArrayOf(0, 0) + lyrics.toByteArray(Charsets.UTF_16)
        assertEquals(lyrics, find(id3(3, "USLT" to body)))
    }

    @Test fun prefersSyncedOverPlainLyrics() {
        val txxx = byteArrayOf(3) + "LYRICS".toByteArray() + byteArrayOf(0) + lyrics.toByteArray()
        assertEquals(lyrics, find(id3(4, "USLT" to uslt("Plain text"), "TXXX" to txxx)))
    }

    private fun comments(text: String): ByteArray {
        val comment = "LYRICS=$text".toByteArray()
        return int(0, ByteOrder.LITTLE_ENDIAN) + int(1, ByteOrder.LITTLE_ENDIAN) +
            int(comment.size, ByteOrder.LITTLE_ENDIAN) + comment
    }

    @Test fun readsFlacComments() {
        val tags = comments(lyrics)
        val header = byteArrayOf(0x84.toByte()) + int(tags.size).copyOfRange(1, 4)
        assertEquals(lyrics, find("fLaC".toByteArray() + header + tags))
    }

    private fun page(payload: ByteArray, segments: ByteArray, continued: Boolean = false): ByteArray {
        val header = ByteArray(27)
        "OggS".toByteArray().copyInto(header)
        header[5] = if (continued) 1 else 2
        header[26] = segments.size.toByte()
        return header + segments + payload
    }

    @Test fun readsOpusAndVorbisComments() {
        for (prefix in listOf("OpusTags".toByteArray(), byteArrayOf(3) + "vorbis".toByteArray())) {
            val packet = prefix + comments(lyrics)
            assertEquals(lyrics, find(page(packet, byteArrayOf(packet.size.toByte()))))
        }
    }

    @Test fun reassemblesCommentsAcrossOggPages() {
        val text = (lyrics + "\n" + "Long metadata ".repeat(30)).trimEnd()
        val packet = "OpusTags".toByteArray() + comments(text)
        val tail = packet.copyOfRange(255, packet.size)
        val segments = ByteArray(tail.size / 255) { 255.toByte() } + byteArrayOf((tail.size % 255).toByte())
        assertEquals(text, find(page(packet.copyOfRange(0, 255), byteArrayOf(255.toByte())) + page(tail, segments, true)))
    }

    private fun atom(name: String, payload: ByteArray): ByteArray =
        int(payload.size + 8) + name.toByteArray(Charsets.ISO_8859_1) + payload

    @Test fun readsM4aLyrics() {
        val data = atom("data", int(1) + int(0) + lyrics.toByteArray())
        val tags = atom("moov", atom("udta", atom("meta", int(0) + atom("ilst", atom("©lyr", data)))))
        assertEquals(lyrics, find(atom("ftyp", "M4A ".toByteArray()) + tags))
    }

    @Test fun ignoresMissingBlankAndTruncatedTags() {
        assertNull(find(byteArrayOf()))
        assertNull(find(id3(3, "USLT" to uslt(" \n "))))
        assertNull(find(id3(4, "USLT" to uslt(lyrics)).copyOf(18)))
        assertNull(find(page("OpusTags".toByteArray() + int(Int.MAX_VALUE, ByteOrder.LITTLE_ENDIAN), byteArrayOf(12))))
    }
}
