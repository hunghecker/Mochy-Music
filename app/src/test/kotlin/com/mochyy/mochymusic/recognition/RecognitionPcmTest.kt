package com.mochyy.mochymusic.recognition

import java.nio.ShortBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionPcmTest {
    @Test fun monoIsUnchanged() {
        assertEquals((-1234).toShort(), RecognitionPcm.mono(ShortBuffer.wrap(shortArrayOf(-1234)), 1))
    }
    @Test fun stereoDoesNotOverflow() {
        assertEquals(32767.toShort(), RecognitionPcm.mono(ShortBuffer.wrap(shortArrayOf(32767, 32767)), 2))
        assertEquals((-32768).toShort(), RecognitionPcm.mono(ShortBuffer.wrap(shortArrayOf(-32768, -32768)), 2))
    }
    @Test fun opposingChannelsCancel() {
        assertEquals(0.toShort(), RecognitionPcm.mono(ShortBuffer.wrap(shortArrayOf(1000, -1000)), 2))
    }
    @Test fun multichannelIsAveragedAndConsumesOneFrame() {
        val pcm = ShortBuffer.wrap(shortArrayOf(1000, 2000, 3000, 4000, 7))
        assertEquals(2500.toShort(), RecognitionPcm.mono(pcm, 4))
        assertEquals(1, pcm.remaining())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedFrame() {
        RecognitionPcm.mono(ShortBuffer.wrap(shortArrayOf(1)), 2)
    }
}
