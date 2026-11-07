package com.mochyy.mochymusic.recognition

/** Stable UI categories; raw provider errors and private file paths stay out of messages. */
class RecognitionFailure(val stage: Stage, cause: Throwable) : Exception(stage.name, cause) {
    enum class Stage { DECODE, PERMISSION, SHORT, RESAMPLE, FINGERPRINT, SERVICE, NO_MATCH, TIMEOUT }
    companion object {
        fun classify(stage: Stage, error: Throwable): Stage = when {
            error is SecurityException -> Stage.PERMISSION
            error.message == "sample_too_short" -> Stage.SHORT
            stage == Stage.SERVICE && error.message.orEmpty().contains("No match", ignoreCase = true) -> Stage.NO_MATCH
            else -> stage
        }
    }
}
