package com.mochyy.mochymusic.recognition

import java.nio.ShortBuffer

internal object RecognitionPcm {
    /** All channels contribute equally, without clipping or overflow. */
    fun mono(pcm: ShortBuffer, channels: Int): Short {
        require(channels in 1..32 && pcm.remaining() >= channels)
        var sum = 0L
        repeat(channels) { sum += pcm.get().toLong() }
        return (sum / channels).toShort()
    }
}
