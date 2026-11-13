package sh.simple.android.voice

object WhisperNative {
    init {
        System.loadLibrary("simple_whisper")
    }

    external fun loadModel(path: String): Long

    external fun freeModel(context: Long)

    external fun transcribe(context: Long, pcm: FloatArray, threads: Int, language: String): String
}
