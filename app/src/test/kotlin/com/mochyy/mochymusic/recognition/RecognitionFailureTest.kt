package com.mochyy.mochymusic.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionFailureTest {
    @Test fun permissionIsNotNoMatch() {
        assertEquals(RecognitionFailure.Stage.PERMISSION, RecognitionFailure.classify(RecognitionFailure.Stage.DECODE, SecurityException()))
    }
    @Test fun shortSampleIsNotServiceError() {
        assertEquals(RecognitionFailure.Stage.SHORT, RecognitionFailure.classify(RecognitionFailure.Stage.RESAMPLE, IllegalArgumentException("sample_too_short")))
    }
    @Test fun noMatchOnlyComesFromService() {
        assertEquals(RecognitionFailure.Stage.NO_MATCH, RecognitionFailure.classify(RecognitionFailure.Stage.SERVICE, Exception("No match found")))
        assertEquals(RecognitionFailure.Stage.DECODE, RecognitionFailure.classify(RecognitionFailure.Stage.DECODE, Exception("No match found")))
    }
    @Test fun providerRejectionIsNotNoMatch() {
        assertEquals(RecognitionFailure.Stage.SERVICE, RecognitionFailure.classify(RecognitionFailure.Stage.SERVICE, Exception("Recognition failed (error 403)")))
    }
    @Test fun decodeFailureKeepsStage() {
        assertEquals(RecognitionFailure.Stage.DECODE, RecognitionFailure.classify(RecognitionFailure.Stage.DECODE, IllegalStateException("no audio track")))
    }
}
