package sh.simple.android.ui.composer

fun voiceMicRingScale(level: Float, reduceMotion: Boolean): Float {
    if (level <= 0f || reduceMotion) return 1f
    return 1f + 0.25f * level.coerceIn(0f, 1f)
}
