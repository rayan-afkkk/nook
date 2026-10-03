package com.nook.core

/** App-wide limits shared by the UI, the data layer and the tests. */
object Limits {
    const val APP_NAME = "NOOK"
    const val LOCK_MIN_LENGTH = 6
    /** Lock the app again after this long in the background. */
    const val LOCK_BACKGROUND_TIMEOUT_MS = 30_000L
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 20
    const val MAX_FILE_BYTES = 10L * 1024 * 1024
    const val PAGE_SIZE = 30L
    const val MAX_GROUP_MEMBERS = 32
    const val CALL_RING_TIMEOUT_MS = 40_000L
    const val MAX_STICKERS_PER_PACK = 60
    const val MAX_TEXT = 4000
}
