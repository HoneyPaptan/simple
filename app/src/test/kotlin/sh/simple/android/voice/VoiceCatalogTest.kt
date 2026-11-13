package sh.simple.android.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCatalogTest {
    @Test
    fun defaultIsTinyEnglish() {
        assertEquals("tiny.en", VoiceCatalog.DEFAULT_ID)
    }

    @Test
    fun downloadUrlsUseHuggingFaceResolve() {
        val spec = VoiceCatalog.find("tiny.en")!!
        assertEquals(
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
            VoiceCatalog.downloadUrl(spec),
        )
    }
}
